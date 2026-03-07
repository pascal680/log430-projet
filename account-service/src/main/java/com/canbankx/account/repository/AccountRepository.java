package com.canbankx.account.repository;

import com.canbankx.account.model.Account;
import com.canbankx.account.model.enums.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByClientId(UUID clientId);

    List<Account> findByType(AccountType type);

    boolean existsByAccountNumber(String accountNumber);
}
