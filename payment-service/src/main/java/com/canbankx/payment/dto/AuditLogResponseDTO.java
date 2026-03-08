package com.canbankx.payment.dto;

import com.canbankx.payment.model.AuditLog;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Schema(description = "Audit trail entry for a transaction")
public class AuditLogResponseDTO {

    @Schema(description = "Audit log entry ID")
    private final UUID id;
    @Schema(description = "Related transaction ID")
    private final UUID transactionId;
    @Schema(description = "Action recorded", example = "SUBMITTED")
    private final String action;
    @Schema(description = "Additional detail about the action", example = "TRANSFER of 100.00 from 2536624609 to 5626038191")
    private final String detail;
    @Schema(description = "When this audit entry was created")
    private final Instant createdAt;

    public AuditLogResponseDTO(AuditLog log) {
        this.id = log.getId();
        this.transactionId = log.getTransactionId();
        this.action = log.getAction();
        this.detail = log.getDetail();
        this.createdAt = log.getCreatedAt();
    }
}
