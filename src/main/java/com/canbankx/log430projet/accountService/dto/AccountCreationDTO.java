package com.canbankx.log430projet.accountService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountCreationDTO {

    @NotBlank(message = "Client ID is required")
    private String clientId;

    @NotBlank(message = "Account type is required")
    private String accountType; // "CHECKING" or "SAVINGS"

    @PositiveOrZero(message = "Initial deposit must be zero or positive")
    private double initialDeposit;
}