package com.canbankx.account.dto;

import com.canbankx.account.model.Account;
import com.canbankx.account.model.enums.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Schema(description = "Account data returned by the API")
public class AccountResponseDTO {

    @Schema(description = "Account unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    private final UUID id;
    @Schema(description = "10-digit account number", example = "2536624609")
    private final String accountNumber;
    @Schema(description = "Current balance", example = "1250.00")
    private final BigDecimal balance;
    @Schema(description = "Account type", example = "CHECKING")
    private final AccountType type;
    @Schema(description = "Owner client ID", example = "9da30a2b-6c75-44ed-bff3-8937d41bc32b")
    private final UUID clientId;
    @Schema(description = "Account creation timestamp")
    private final Instant createdAt;
    @Schema(description = "Last update timestamp")
    private final Instant updatedAt;

    public AccountResponseDTO(Account account) {
        this.id = account.getId();
        this.accountNumber = account.getAccountNumber();
        this.balance = account.getBalance();
        this.type = account.getType();
        this.clientId = account.getClientId();
        this.createdAt = account.getCreatedAt();
        this.updatedAt = account.getUpdatedAt();
    }
}