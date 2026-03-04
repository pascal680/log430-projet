package com.canbankx.log430projet.identityService.model.enums;

import lombok.Getter;

@Getter
public enum Status {
    ACTIVE("Active"),
    INACTIVE("Inactive"),
    CLOSED("Closed");

    private final String value;

    Status(String value) {
        this.value = value;
    }
}
