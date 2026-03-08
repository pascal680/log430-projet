package com.canbankx.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Schema(description = "Payload to open a new bank account")
public class AccountCreationDTO {

    @NotBlank(message = "Client ID is required")
    @Schema(description = "UUID of the client who owns this account", example = "550e8400-e29b-41d4-a716-446655440000")
    private String clientId;

    @NotBlank(message = "Account type is required")
    @Schema(description = "Account type: CHECKING or SAVINGS", example = "CHECKING")
    private String accountType; // "CHECKING" or "SAVINGS"

    @PositiveOrZero(message = "Initial deposit must be zero or positive")
    @Schema(description = "Opening deposit amount", example = "500.00")
    private BigDecimal initialDeposit = BigDecimal.ZERO;
}