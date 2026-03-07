package com.canbankx.identity.service;

import com.canbankx.identity.common.exceptions.ClientNotFoundException;
import com.canbankx.identity.dto.ClientRegistrationDTO;
import com.canbankx.identity.model.Client;
import com.canbankx.identity.model.enums.Status;
import com.canbankx.identity.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    // new clients start as PENDING until activated
    @Transactional
    public Client register(ClientRegistrationDTO dto) {
        if (clientRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email already in use: " + dto.getEmail());
        }
        if (clientRepository.existsByNas(dto.getNas())) {
            throw new IllegalArgumentException("NAS already registered.");
        }

        Client client = Client.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .phoneNumber(dto.getPhoneNumber())
                .address(dto.getAddress())
                .nas(dto.getNas())
                .status(Status.PENDING)
                .build();

        return clientRepository.save(client);
    }

    @Transactional
    public Client activate(UUID id) {
        Client client = getById(id);
        if (client.getStatus() == Status.ACTIVE) {
            throw new IllegalArgumentException("Client is already ACTIVE.");
        }
        client.setStatus(Status.ACTIVE);
        return clientRepository.save(client);
    }

    @Transactional(readOnly = true)
    public Client authenticate(String email, String rawPassword) {
        Client client = clientRepository.findByEmail(email)
                .orElseThrow(() -> new ClientNotFoundException(email));
        if (!passwordEncoder.matches(rawPassword, client.getPassword())) {
            throw new IllegalArgumentException("Invalid credentials.");
        }
        if (client.getStatus() != Status.ACTIVE) {
            throw new IllegalStateException("Account is not ACTIVE. Current status: " + client.getStatus());
        }
        return client;
    }

    @Transactional(readOnly = true)
    public Client getById(UUID id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ClientNotFoundException(id.toString()));
    }

    @Transactional(readOnly = true)
    public List<Client> getAll() {
        return clientRepository.findAll();
    }

    @Transactional
    public Client updateStatus(UUID id, Status newStatus) {
        Client client = getById(id);
        client.setStatus(newStatus);
        return clientRepository.save(client);
    }

    @Transactional
    public void deactivate(UUID id) {
        updateStatus(id, Status.INACTIVE);
    }
}
