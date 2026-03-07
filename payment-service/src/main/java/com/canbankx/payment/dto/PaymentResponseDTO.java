package com.canbankx.payment.dto;

import com.canbankx.payment.model.BankTransaction;
import com.canbankx.payment.model.enums.TransactionStatus;
import com.canbankx.payment.model.enums.TransactionType;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class PaymentResponseDTO {

    private final UUID id;
    private final String idempotencyKey;
    private final String sourceAccountNumber;
    private final String targetAccountNumber;
    private final BigDecimal amount;
    private final TransactionType type;
    private final TransactionStatus status;
    private final String auditNote;
    private final Instant createdAt;
    private final Instant updatedAt;

    public PaymentResponseDTO(BankTransaction tx) {
        this.id = tx.getId();
        this.idempotencyKey = tx.getIdempotencyKey();
        this.sourceAccountNumber = tx.getSourceAccountNumber();
        this.targetAccountNumber = tx.getTargetAccountNumber();
        this.amount = tx.getAmount();
        this.type = tx.getType();
        this.status = tx.getStatus();
        this.auditNote = tx.getAuditNote();
        this.createdAt = tx.getCreatedAt();
        this.updatedAt = tx.getUpdatedAt();
    }
}
