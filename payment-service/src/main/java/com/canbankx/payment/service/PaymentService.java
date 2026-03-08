package com.canbankx.payment.service;

import com.canbankx.payment.client.AccountClient;
import com.canbankx.payment.client.IdentityClient;
import com.canbankx.payment.dto.AccountInfoDTO;
import com.canbankx.payment.dto.ClientInfoDTO;
import com.canbankx.payment.exceptions.PaymentNotFoundException;
import com.canbankx.payment.dto.PaymentRequestDTO;
import com.canbankx.payment.model.AuditLog;
import com.canbankx.payment.model.BankTransaction;
import com.canbankx.payment.model.enums.TransactionStatus;
import com.canbankx.payment.model.enums.TransactionType;
import com.canbankx.payment.repository.AuditLogRepository;
import com.canbankx.payment.repository.BankTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Pageable;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final String REDIS_KEY_PREFIX = "payment:idem:";
    private static final Duration IDEM_TTL = Duration.ofHours(24);

    private final BankTransactionRepository transactionRepository;
    private final AuditLogRepository auditLogRepository;
    private final EmailService emailService;
    private final AccountClient accountClient;
    private final IdentityClient identityClient;
    private final StringRedisTemplate redisTemplate;

    /** Background thread pool for fire-and-forget email notifications. */
    @Qualifier("emailTaskExecutor")
    private final Executor emailTaskExecutor;

    public BankTransaction submit(String idempotencyKey, PaymentRequestDTO dto) {
        String redisKey = REDIS_KEY_PREFIX + idempotencyKey;

        // duplicate check
        String existingId = redisTemplate.opsForValue().get(redisKey);
        if (existingId != null) {
            log.info("Duplicate payment key [{}] → returning existing tx [{}]", idempotencyKey, existingId);
            return transactionRepository.findById(UUID.fromString(existingId))
                    .orElseGet(() -> transactionRepository.findByIdempotencyKey(idempotencyKey)
                            .orElseThrow(() -> new PaymentNotFoundException(existingId)));
        }

        // DB fallback in case Redis was cleared
        Optional<BankTransaction> existingByKey = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingByKey.isPresent()) {
            BankTransaction existing = existingByKey.get();
            switch (existing.getStatus()) {
                case COMPLETED -> {
                    log.info("Idempotency key [{}] already COMPLETED in DB (Redis miss) – returning existing tx [{}]",
                            idempotencyKey, existing.getId());
                    redisTemplate.opsForValue().set(redisKey, existing.getId().toString(), IDEM_TTL);
                    return existing;
                }
                case PENDING -> {
                    log.warn("Idempotency key [{}] is already PENDING – possible concurrent request", idempotencyKey);
                    throw new IllegalStateException(
                            "A transaction with this idempotency key is already in progress. Please wait or use a new key.");
                }
                case FAILED -> {
                    log.info("Idempotency key [{}] had a FAILED transaction [{}] – deleting and allowing retry",
                            idempotencyKey, existing.getId());
                    transactionRepository.delete(existing);
                }
            }
        }

        if (dto.getType() == TransactionType.TRANSFER &&
                (dto.getTargetAccountNumber() == null || dto.getTargetAccountNumber().isBlank())) {
            throw new IllegalArgumentException("TRANSFER transactions require a targetAccountNumber.");
        }

        BankTransaction tx = persistPending(idempotencyKey, dto);
        String txRef = tx.getId().toString();

        boolean sourceDebited = false;
        try {
            if (dto.getType() == TransactionType.CREDIT) {
                // Deposit: only credit the source account — no prior debit.
                // (Previously debit+credit was applied to the same account = net zero, which was a bug.)
                accountClient.credit(dto.getSourceAccountNumber(), dto.getAmount(), txRef);
                auditStep(tx.getId(), "BALANCE_CREDITED",
                        "Deposited " + dto.getAmount() + " into " + dto.getSourceAccountNumber());
            } else {
                // DEBIT (withdrawal) or TRANSFER: always debit the source account first.
                accountClient.debit(dto.getSourceAccountNumber(), dto.getAmount(), txRef);
                sourceDebited = true;
                auditStep(tx.getId(), "BALANCE_DEBITED",
                        "Debited " + dto.getAmount() + " from " + dto.getSourceAccountNumber());

                if (dto.getType() == TransactionType.TRANSFER) {
                    accountClient.credit(dto.getTargetAccountNumber(), dto.getAmount(), txRef);
                    auditStep(tx.getId(), "BALANCE_CREDITED",
                            "Credited " + dto.getAmount() + " to " + dto.getTargetAccountNumber());
                }
            }

            tx = completeTransaction(tx.getId());
            redisTemplate.opsForValue().set(redisKey, txRef, IDEM_TTL);
            log.info("Payment [{}] COMPLETED. Idempotency key cached {} h.", txRef, IDEM_TTL.toHours());

            // ── Send payment confirmation email (fire-and-forget) ─────────────
            sendConfirmationEmail(tx);

        } catch (Exception ex) {
            if (sourceDebited && dto.getType() == TransactionType.TRANSFER) {
                try {
                    accountClient.credit(dto.getSourceAccountNumber(), dto.getAmount(), txRef + "-COMPENSATION");
                    auditStep(tx.getId(), "COMPENSATION_CREDIT", "Compensation credit applied after failed transfer.");
                } catch (Exception compensationEx) {
                    log.error("CRITICAL: Compensation credit failed for tx [{}]: {}", txRef, compensationEx.getMessage());
                }
            }
            failTransaction(tx.getId(), ex.getMessage());
            log.error("Payment [{}] FAILED: {}", txRef, ex.getMessage());
            // Re-throw the original exception to preserve its HTTP status code
            // (e.g. HttpClientErrorException.UnprocessableEntity for 422 insufficient funds,
            //       HttpClientErrorException.NotFound for 404 account not found).
            if (ex instanceof RuntimeException rte) {
                throw rte;
            }
            throw new RuntimeException("Payment failed: " + ex.getMessage(), ex);
        }

        return tx;
    }

    @Transactional(readOnly = true)
    public BankTransaction getById(UUID id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id.toString()));
    }

    @Transactional(readOnly = true)
    public List<BankTransaction> getByAccountNumber(String accountNumber) {
        return transactionRepository.findBySourceAccountNumber(accountNumber);
    }

    @Transactional(readOnly = true)
    public List<BankTransaction> getByAccountNumber(String accountNumber, Pageable pageable) {
        return transactionRepository.findBySourceAccountNumber(accountNumber, pageable);
    }

    @Transactional(readOnly = true)
    public List<BankTransaction> getRecentByAccountNumber(String accountNumber) {
        return transactionRepository.findTop10BySourceAccountNumberOrderByCreatedAtDesc(accountNumber);
    }

    @Transactional(readOnly = true)
    public List<BankTransaction> getAll() {
        return transactionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<BankTransaction> getAll(Pageable pageable) {
        return transactionRepository.findAllBy(pageable);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected BankTransaction persistPending(String idempotencyKey, PaymentRequestDTO dto) {
        BankTransaction tx = BankTransaction.builder()
                .idempotencyKey(idempotencyKey)
                .sourceAccountNumber(dto.getSourceAccountNumber())
                .targetAccountNumber(dto.getTargetAccountNumber())
                .amount(dto.getAmount())
                .type(dto.getType())
                .status(TransactionStatus.PENDING)
                .build();
        tx = transactionRepository.save(tx);

        auditLogRepository.save(AuditLog.builder()
                .transactionId(tx.getId())
                .action("TRANSFER_INITIATED")
                .detail("Type=" + dto.getType() + ", Amount=" + dto.getAmount()
                        + ", Source=" + dto.getSourceAccountNumber())
                .build());

        return tx;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void auditStep(UUID txId, String action, String detail) {
        auditLogRepository.save(AuditLog.builder()
                .transactionId(txId).action(action).detail(detail).build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected BankTransaction completeTransaction(UUID txId) {
        BankTransaction tx = transactionRepository.findById(txId)
                .orElseThrow(() -> new PaymentNotFoundException(txId.toString()));
        tx.setStatus(TransactionStatus.COMPLETED);
        tx.setAuditNote("Transaction processed successfully.");
        tx = transactionRepository.save(tx);
        auditLogRepository.save(AuditLog.builder()
                .transactionId(txId).action("TRANSFER_COMPLETED").detail("All balance updates applied.").build());
        return tx;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void failTransaction(UUID txId, String reason) {
        transactionRepository.findById(txId).ifPresent(tx -> {
            tx.setStatus(TransactionStatus.FAILED);
            tx.setAuditNote("Failed: " + reason);
            transactionRepository.save(tx);
        });
        auditLogRepository.save(AuditLog.builder()
                .transactionId(txId).action("TRANSFER_FAILED").detail(reason).build());
    }

    /**
     * Submits the entire email confirmation flow (account lookup + client lookup + SMTP)
     * to the background executor so the payment HTTP response is returned immediately.
     */
    private void sendConfirmationEmail(BankTransaction tx) {
        CompletableFuture.runAsync(() -> {
            try {
                AccountInfoDTO account = accountClient.getAccountByNumber(tx.getSourceAccountNumber());
                if (account == null || account.clientId() == null) {
                    log.warn("Could not resolve clientId for account [{}] – email skipped.", tx.getSourceAccountNumber());
                    return;
                }
                ClientInfoDTO client = identityClient.getClientById(account.clientId());
                if (client == null || client.email() == null) {
                    log.warn("Could not resolve email for clientId [{}] – email skipped.", account.clientId());
                    return;
                }
                emailService.sendTransactionConfirmation(client.email(), client.firstName(), tx);
            } catch (Exception e) {
                log.warn("Failed to send confirmation email for tx [{}]: {}", tx.getId(), e.getMessage());
            }
        }, emailTaskExecutor);
    }
}