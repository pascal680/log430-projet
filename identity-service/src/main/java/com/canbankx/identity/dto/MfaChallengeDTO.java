package com.canbankx.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MfaChallengeDTO {
    private final String challengeToken;
    private final String message;
}
