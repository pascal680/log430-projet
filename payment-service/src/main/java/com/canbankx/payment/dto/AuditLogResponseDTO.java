package com.canbankx.payment.dto;

import com.canbankx.payment.model.AuditLog;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class AuditLogResponseDTO {

    private final UUID id;
    private final UUID transactionId;
    private final String action;
    private final String detail;
    private final Instant createdAt;

    public AuditLogResponseDTO(AuditLog log) {
        this.id = log.getId();
        this.transactionId = log.getTransactionId();
        this.action = log.getAction();
        this.detail = log.getDetail();
        this.createdAt = log.getCreatedAt();
    }
}
