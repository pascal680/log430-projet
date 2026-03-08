package com.canbankx.payment.service;

import com.canbankx.payment.model.BankTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' HH:mm:ss 'UTC'")
                    .withZone(ZoneId.of("UTC"));

    private final JavaMailSender mailSender;

    /**
     * Sends a payment confirmation email.
     * Called from within the emailTaskExecutor background thread – never blocks the request thread.
     */
    public void sendTransactionConfirmation(String toEmail, String firstName, BankTransaction tx) {
        String subject = buildSubject(tx);
        String body    = buildBody(firstName, tx);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("no-reply@canbankx.ca");
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
        log.info("Payment confirmation email sent to {} for transaction [{}]", toEmail, tx.getId());
    }

    // ...existing code...

    // ── helpers ──────────────────────────────────────────────────────────────

    private String buildSubject(BankTransaction tx) {
        return switch (tx.getType()) {
            case DEBIT    -> "CanBankX – Debit Confirmation #" + tx.getId();
            case CREDIT   -> "CanBankX – Credit Confirmation #" + tx.getId();
            case TRANSFER -> "CanBankX – Transfer Confirmation #" + tx.getId();
        };
    }

    private String buildBody(String firstName, BankTransaction tx) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hello ").append(firstName).append(",\n\n");

        sb.append("Your ").append(tx.getType().name().toLowerCase())
          .append(" has been processed successfully.\n\n");

        sb.append("──────────────────────────────\n");
        sb.append("Transaction Details\n");
        sb.append("──────────────────────────────\n");
        sb.append("Transaction ID : ").append(tx.getId()).append("\n");
        sb.append("Type           : ").append(tx.getType()).append("\n");
        sb.append("Amount         : $").append(tx.getAmount()).append("\n");
        sb.append("From account   : ").append(tx.getSourceAccountNumber()).append("\n");

        if (tx.getTargetAccountNumber() != null && !tx.getTargetAccountNumber().isBlank()) {
            sb.append("To account     : ").append(tx.getTargetAccountNumber()).append("\n");
        }

        sb.append("Status         : ").append(tx.getStatus()).append("\n");
        sb.append("Date           : ").append(FORMATTER.format(tx.getUpdatedAt())).append("\n");
        sb.append("──────────────────────────────\n\n");

        sb.append("If you did not initiate this transaction, please contact CanBankX support immediately.\n\n");
        sb.append("– CanBankX Team");
        return sb.toString();
    }
}
