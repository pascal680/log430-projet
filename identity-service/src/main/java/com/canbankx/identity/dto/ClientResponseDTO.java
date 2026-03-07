package com.canbankx.identity.dto;

import com.canbankx.identity.model.Client;
import com.canbankx.identity.model.enums.Status;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class ClientResponseDTO {

    private final UUID id;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String phoneNumber;
    private final String address;
    private final Status status;
    private final Instant createdAt;
    private final Instant updatedAt;

    public ClientResponseDTO(Client client) {
        this.id = client.getId();
        this.firstName = client.getFirstName();
        this.lastName = client.getLastName();
        this.email = client.getEmail();
        this.phoneNumber = client.getPhoneNumber();
        this.address = client.getAddress();
        this.status = client.getStatus();
        this.createdAt = client.getCreatedAt();
        this.updatedAt = client.getUpdatedAt();
    }
}
