package com.canbankx.account.service;

import com.canbankx.account.exceptions.AccountNotFoundException;
import com.canbankx.account.exceptions.InsufficientFundsException;
import com.canbankx.account.exceptions.InvalidAccountTypeException;
import com.canbankx.account.dto.AccountCreationDTO;
import com.canbankx.account.model.Account;
import com.canbankx.account.model.enums.AccountType;
import com.canbankx.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public Account createAccount(AccountCreationDTO dto) {
        AccountType accountType;
        try {
            accountType = AccountType.valueOf(dto.getAccountType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidAccountTypeException(dto.getAccountType());
        }

        Account account = Account.builder()
                .clientId(UUID.fromString(dto.getClientId()))
                .type(accountType)
                .balance(dto.getInitialDeposit())
                .accountNumber(generateUniqueAccountNumber())
                .build();

        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Account getAccountById(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id.toString()));
    }

    @Transactional(readOnly = true)
    public Account getAccountByNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    @Transactional(readOnly = true)
    public List<Account> getAccountsByClientId(UUID clientId) {
        return accountRepository.findByClientId(clientId);
    }

    @Transactional(readOnly = true)
    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    /**
     * Atomically debits the account.  Returns void so the exclusive row lock
     * is released as soon as this transaction commits — the caller fetches the
     * updated balance in a separate read-only transaction.
     */
    @Transactional
    public void debit(String accountNumber, BigDecimal amount) {
        int updated = accountRepository.atomicDebit(accountNumber, amount);
        if (updated == 0) {
            if (!accountRepository.findByAccountNumber(accountNumber).isPresent()) {
                throw new AccountNotFoundException(accountNumber);
            }
            throw new InsufficientFundsException(accountNumber);
        }
        // No post-UPDATE SELECT here — lock released on commit immediately.
    }

    /**
     * Atomically credits the account.  Same pattern as debit.
     */
    @Transactional
    public void credit(String accountNumber, BigDecimal amount) {
        int updated = accountRepository.atomicCredit(accountNumber, amount);
        if (updated == 0) {
            throw new AccountNotFoundException(accountNumber);
        }
    }

    private String generateUniqueAccountNumber() {
        String number;
        do {
            long raw = 1_000_000_000L + (long) (RANDOM.nextDouble() * 9_000_000_000L);
            number = String.valueOf(raw);
        } while (accountRepository.existsByAccountNumber(number));
        return number;
    }
}
