package com.canbankx.payment.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicatePaymentException extends RuntimeException {

    public DuplicatePaymentException(String idempotencyKey) {
        super("A transaction with idempotency key '" + idempotencyKey + "' already exists.");
    }
}
