package com.canbankx.account.dto;

import com.canbankx.account.model.Account;
import com.canbankx.account.model.enums.AccountType;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class AccountResponseDTO {

    private final UUID id;
    private final String accountNumber;
    private final BigDecimal balance;
    private final AccountType type;
    private final UUID clientId;
    private final Instant createdAt;
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
