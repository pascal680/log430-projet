package com.canbankx.account.repository;

import com.canbankx.account.model.Account;
import com.canbankx.account.model.enums.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByClientId(UUID clientId);

    List<Account> findByType(AccountType type);

    boolean existsByAccountNumber(String accountNumber);

    /**
     * Atomically debit an account only when sufficient balance exists.
     * Returns 1 on success, 0 when balance is insufficient (no row matched).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Account a SET a.balance = a.balance - :amount " +
           "WHERE a.accountNumber = :accountNumber AND a.balance >= :amount")
    int atomicDebit(@Param("accountNumber") String accountNumber,
                    @Param("amount") BigDecimal amount);

    /**
     * Atomically credit an account.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Account a SET a.balance = a.balance + :amount " +
           "WHERE a.accountNumber = :accountNumber")
    int atomicCredit(@Param("accountNumber") String accountNumber,
                     @Param("amount") BigDecimal amount);
}
