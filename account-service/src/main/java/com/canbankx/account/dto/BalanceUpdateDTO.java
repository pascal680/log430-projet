package com.canbankx.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Schema(description = "Payload for a balance debit or credit operation")
public class BalanceUpdateDTO {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @Schema(description = "Amount to apply", example = "100.00")
    private BigDecimal amount;

    @Schema(description = "Originating transaction UUID for traceability", example = "7660d0b6-58bf-4daf-9238-a540d335cc1f")
    private String transactionRef;
}
