package com.canbankx.account.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_operations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankPaymentOperation {

    @EmbeddedId
    private BankPaymentOperationId id;

    @Column(nullable = false)
    private String accountNumber;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant processedAt;

    public BankPaymentOperation(String paymentId, String operation, String accountNumber, BigDecimal amount) {
        this.id = new BankPaymentOperationId(paymentId, operation);
        this.accountNumber = accountNumber;
        this.amount = amount;
        this.processedAt = Instant.now();
    }

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankPaymentOperationId implements Serializable {

        @Column(name = "payment_id", nullable = false, length = 80)
        private String paymentId;

        @Column(name = "operation", nullable = false, length = 20)
        private String operation;
    }
}
