package com.canbankx.log430projet.common.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidAccountTypeException extends RuntimeException {

    public InvalidAccountTypeException(String type) {
        super("Invalid account type: '" + type + "'. Accepted values: CHECKING, SAVINGS");
    }
}
