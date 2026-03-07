package com.canbankx.payment.dto;

import com.canbankx.payment.model.enums.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PaymentRequestDTO {

    @NotBlank(message = "Source account number is required")
    private String sourceAccountNumber;

    private String targetAccountNumber;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Transaction type is required")
    private TransactionType type;
}
