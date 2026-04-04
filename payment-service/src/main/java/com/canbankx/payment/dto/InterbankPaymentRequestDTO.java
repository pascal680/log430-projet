package com.canbankx.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class InterbankPaymentRequestDTO {

    @NotBlank(message = "idempotencyKey is required")
    private String idempotencyKey;

    @NotBlank(message = "senderAccountNumber is required")
    private String senderAccountNumber;

    @NotBlank(message = "recipientAccountNumber is required")
    private String recipientAccountNumber;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
    @Digits(integer = 13, fraction = 2, message = "amount format is invalid")
    private BigDecimal amount;

    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code")
    private String currency = "CAD";
}
