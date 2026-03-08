package com.canbankx.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Minimal projection of the account-service AccountResponseDTO.
 * Only the fields the payment-service needs for email lookup are mapped.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountInfoDTO(UUID clientId, String accountNumber) {}
