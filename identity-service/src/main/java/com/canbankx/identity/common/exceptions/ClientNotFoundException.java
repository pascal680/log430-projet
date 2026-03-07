package com.canbankx.identity.common.exceptions;

import com.canbankx.common.exceptions.ResourceNotFoundException;

public class ClientNotFoundException extends ResourceNotFoundException {

    public ClientNotFoundException(String id) {
        super("Client not found with id: " + id);
    }
}
