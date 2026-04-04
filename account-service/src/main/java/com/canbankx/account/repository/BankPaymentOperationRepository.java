package com.canbankx.account.repository;

import com.canbankx.account.model.BankPaymentOperation;
import com.canbankx.account.model.BankPaymentOperation.BankPaymentOperationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BankPaymentOperationRepository extends JpaRepository<BankPaymentOperation, BankPaymentOperationId> {

    boolean existsByIdPaymentIdAndIdOperation(String paymentId, String operation);
}
