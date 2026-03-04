package com.canbankx.log430projet.accountService.repository;

import com.canbankx.log430projet.accountService.model.Account;
import com.canbankx.log430projet.accountService.model.enums.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    // Create / update an account → use save(account) inherited from JpaRepository

    // Find account by its unique account number
    Optional<Account> findByAccountNumber(String accountNumber);

    // Find all accounts belonging to a client
    List<Account> findByClientId(UUID clientId);

    // Find all accounts of a given type
    List<Account> findByType(AccountType type);

    // Check whether an account number already exists
    boolean existsByAccountNumber(String accountNumber);
}
