package com.canbankx.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountSummaryDTO {

    private UUID accountId;
    private String accountNumber;
    private BigDecimal balance;
    private String accountType;
    private Instant lastUpdated;
}
