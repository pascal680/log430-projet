package com.canbankx.payment.model;

import com.canbankx.payment.model.enums.TransactionStatus;
import com.canbankx.payment.model.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "bank_transactions",
    indexes = {
        @Index(name = "idx_idempotency_key", columnList = "idempotencyKey", unique = true),
        @Index(name = "idx_source_account",  columnList = "sourceAccountNumber")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String idempotencyKey;

    @Column(nullable = false)
    private String sourceAccountNumber;

    private String targetAccountNumber;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(length = 512)
    private String auditNote;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
