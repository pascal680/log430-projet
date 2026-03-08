package com.canbankx.payment.dto;

import com.canbankx.payment.model.BankTransaction;
import com.canbankx.payment.model.enums.TransactionStatus;
import com.canbankx.payment.model.enums.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Schema(description = "Transaction data returned by the API")
public class PaymentResponseDTO {

    @Schema(description = "Transaction unique identifier")
    private final UUID id;
    @Schema(description = "Client-supplied idempotency key")
    private final String idempotencyKey;
    @Schema(description = "Source account number", example = "2536624609")
    private final String sourceAccountNumber;
    @Schema(description = "Target account number (transfers only)", example = "5626038191")
    private final String targetAccountNumber;
    @Schema(description = "Transaction amount", example = "100.00")
    private final BigDecimal amount;
    @Schema(description = "Transaction type", example = "TRANSFER")
    private final TransactionType type;
    @Schema(description = "Transaction status", example = "COMPLETED")
    private final TransactionStatus status;
    @Schema(description = "Audit note from processing")
    private final String auditNote;
    @Schema(description = "Creation timestamp")
    private final Instant createdAt;
    @Schema(description = "Last update timestamp")
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