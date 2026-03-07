package com.canbankx.payment.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "audit_log",
    indexes = {
        @Index(name = "idx_audit_tx_id",  columnList = "transactionId"),
        @Index(name = "idx_audit_created", columnList = "createdAt")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(updatable = false, nullable = false)
    private UUID transactionId;

    @Column(updatable = false, nullable = false, length = 64)
    private String action;

    @Column(updatable = false, length = 1024)
    private String detail;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private Instant createdAt;
}
