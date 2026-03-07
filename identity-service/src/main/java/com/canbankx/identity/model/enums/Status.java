package com.canbankx.identity.model.enums;

import lombok.Getter;

@Getter
public enum Status {
    ACTIVE("Active"),
    INACTIVE("Inactive"),
    CLOSED("Closed"),
    PENDING("Pending");

    private final String value;

    Status(String value) {
        this.value = value;
    }
}
