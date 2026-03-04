package com.canbankx.log430projet.common.exceptions;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class ErrorResponse {

    private int status;
    private String error;
    private List<String> messages;
    private LocalDateTime timestamp;

    // Constructor for single message
    public ErrorResponse(int status, String error, String message) {
        this.status = status;
        this.error = error;
        this.messages = List.of(message);
        this.timestamp = LocalDateTime.now();
    }
}
