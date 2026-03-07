package com.canbankx.account.exceptions;

import com.canbankx.common.exceptions.ResourceNotFoundException;

public class AccountNotFoundException extends ResourceNotFoundException {

    public AccountNotFoundException(String id) {
        super("Account not found with id: " + id);
    }
}
