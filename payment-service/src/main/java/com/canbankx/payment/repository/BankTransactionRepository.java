package com.canbankx.payment.repository;

import com.canbankx.payment.model.BankTransaction;
import com.canbankx.payment.model.enums.TransactionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankTransactionRepository extends JpaRepository<BankTransaction, UUID> {

    Optional<BankTransaction> findByIdempotencyKey(String idempotencyKey);

    List<BankTransaction> findBySourceAccountNumber(String accountNumber);

    /** Paginated version – use this in list endpoints to avoid loading all rows. */
    List<BankTransaction> findBySourceAccountNumber(String accountNumber, Pageable pageable);

    List<BankTransaction> findByStatus(TransactionStatus status);

    List<BankTransaction> findTop10BySourceAccountNumberOrderByCreatedAtDesc(String accountNumber);

    /** Paginated findAll – use instead of the inherited unbounded findAll(). */
    List<BankTransaction> findAllBy(Pageable pageable);
}
