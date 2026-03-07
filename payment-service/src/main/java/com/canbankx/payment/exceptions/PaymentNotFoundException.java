package com.canbankx.payment.exceptions;

import com.canbankx.common.exceptions.ResourceNotFoundException;

public class PaymentNotFoundException extends ResourceNotFoundException {

    public PaymentNotFoundException(String id) {
        super("Transaction not found with id: " + id);
    }
}
