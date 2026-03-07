package com.canbankx.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthResponseDTO {
    private final String status;
    private final String message;
    private final String clientId;
}
