package com.canbankx.log430projet.paymentService.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Entity
public class BankTransaction {

    @Id
    private String idempotencyKey;

    private double amount;

    private OffsetDateTime createdAt;

    private String accountNumber;
}
