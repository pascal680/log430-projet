package com.canbankx.account.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String accountNumber) {
        super("Insufficient funds on account: " + accountNumber);
    }
}
