package com.canbankx.common.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceUpdateRequestDTO {

    public enum Operation { DEBIT, CREDIT }

    @NotNull
    private Operation operation;

    @NotNull
    @Positive
    private BigDecimal amount;

    /** UUID of the BankTransaction in payment-service – for cross-service traceability. */
    private String transactionRef;
}
