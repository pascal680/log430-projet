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

    @Transactional
    public Account debit(String accountNumber, BigDecimal amount) {
        Account account = getAccountByNumber(accountNumber);
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(accountNumber);
        }
        account.setBalance(account.getBalance().subtract(amount));
        return accountRepository.save(account);
    }

    @Transactional
    public Account credit(String accountNumber, BigDecimal amount) {
        Account account = getAccountByNumber(accountNumber);
        account.setBalance(account.getBalance().add(amount));
        return accountRepository.save(account);
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
