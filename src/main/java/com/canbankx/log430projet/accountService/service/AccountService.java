package com.canbankx.log430projet.accountService.service;

import com.canbankx.log430projet.accountService.dto.AccountCreationDTO;
import com.canbankx.log430projet.accountService.model.Account;
import com.canbankx.log430projet.accountService.model.enums.AccountType;
import com.canbankx.log430projet.accountService.repository.AccountRepository;
import com.canbankx.log430projet.common.exceptions.AccountNotFoundException;
import com.canbankx.log430projet.common.exceptions.InvalidAccountTypeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private static final SecureRandom random = new SecureRandom();

    /**
     * Creates a new bank account from the given DTO.
     *
     * @param dto contains clientId, accountType and initialDeposit
     * @return the persisted Account entity
     */
    @Transactional
    public Account createAccount(AccountCreationDTO dto) {
        // Validate account type
        AccountType accountType;
        try {
            accountType = AccountType.valueOf(dto.getAccountType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidAccountTypeException(dto.getAccountType());
        }

        Account account = new Account();
        account.setClientId(UUID.fromString(dto.getClientId()));
        account.setType(accountType);
        account.setBalance(dto.getInitialDeposit());
        account.setAccountNumber(generateUniqueAccountNumber());

        return accountRepository.save(account);
    }

    public Account getAccountById(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id.toString()));
    }

    // Generates a unique 10-digit account number
    private String generateUniqueAccountNumber() {
        String accountNumber;
        do {
            long number = 1_000_000_000L + (long) (random.nextDouble() * 9_000_000_000L);
            accountNumber = String.valueOf(number);
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }
}