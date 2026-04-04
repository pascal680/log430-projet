package com.canbankx.account.service;

import com.canbankx.account.model.BankPaymentOperation;
import com.canbankx.account.repository.BankPaymentOperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterbankAccountService {

    private static final String DEBIT = "DEBIT";
    private static final String CREDIT = "CREDIT";

    private final AccountService accountService;
    private final BankPaymentOperationRepository operationRepository;

    @Transactional
    public void applyDebit(String accountNumber, BigDecimal amount, String paymentId) {
        if (alreadyProcessed(paymentId, DEBIT)) {
            log.warn("Interbank debit already applied for paymentId={}, skipping", paymentId);
            return;
        }

        accountService.debit(accountNumber, amount);
        operationRepository.save(new BankPaymentOperation(paymentId, DEBIT, accountNumber, amount));
    }

    @Transactional
    public void applyCredit(String accountNumber, BigDecimal amount, String paymentId) {
        if (alreadyProcessed(paymentId, CREDIT)) {
            log.warn("Interbank credit already applied for paymentId={}, skipping", paymentId);
            return;
        }

        accountService.credit(accountNumber, amount);
        operationRepository.save(new BankPaymentOperation(paymentId, CREDIT, accountNumber, amount));
    }

    private boolean alreadyProcessed(String paymentId, String operation) {
        return operationRepository.existsByIdPaymentIdAndIdOperation(paymentId, operation);
    }
}
