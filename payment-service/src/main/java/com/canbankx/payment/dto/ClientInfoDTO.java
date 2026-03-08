package com.canbankx.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Minimal projection of the identity-service ClientResponseDTO.
 * Only the fields the payment-service needs for the confirmation email are mapped.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientInfoDTO(String email, String firstName) {}
