package com.canbankx.payment.repository;

import com.canbankx.payment.model.BankTransaction;
import com.canbankx.payment.model.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankTransactionRepository extends JpaRepository<BankTransaction, UUID> {

    Optional<BankTransaction> findByIdempotencyKey(String idempotencyKey);

    List<BankTransaction> findBySourceAccountNumber(String accountNumber);

    List<BankTransaction> findByStatus(TransactionStatus status);

    List<BankTransaction> findTop10BySourceAccountNumberOrderByCreatedAtDesc(String accountNumber);
}
