package com.canbankx.payment.dto;

import com.canbankx.payment.model.enums.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Schema(description = "Payload to submit a payment or transfer")
public class PaymentRequestDTO {

    @NotBlank(message = "Source account number is required")
    @Schema(description = "Account number to debit", example = "2536624609")
    private String sourceAccountNumber;

    @Schema(description = "Account number to credit (required for TRANSFER)", example = "5626038191")
    private String targetAccountNumber;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @Schema(description = "Transaction amount", example = "100.00")
    private BigDecimal amount;

    @NotNull(message = "Transaction type is required")
    @Schema(description = "DEPOSIT, WITHDRAWAL, or TRANSFER", example = "TRANSFER")
    private TransactionType type;
}
