package com.canbankx.identity.service;

import com.canbankx.identity.common.exceptions.ClientNotFoundException;
import com.canbankx.identity.dto.ClientRegistrationDTO;
import com.canbankx.identity.model.Client;
import com.canbankx.identity.model.enums.Status;
import com.canbankx.identity.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService {

    private static final int OTP_EXPIRY_MINUTES = 10;

    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    // new clients start as PENDING until they verify their email OTP
    @Transactional
    public Client register(ClientRegistrationDTO dto) {
        if (clientRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email already in use: " + dto.getEmail());
        }
        if (clientRepository.existsByNas(dto.getNas())) {
            throw new IllegalArgumentException("NAS already registered.");
        }

        String otp = generateOtp();

        Client client = Client.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .phoneNumber(dto.getPhoneNumber())
                .address(dto.getAddress())
                .nas(dto.getNas())
                .status(Status.PENDING)
                .otpCode(otp)
                .otpExpiry(Instant.now().plusSeconds(OTP_EXPIRY_MINUTES * 60L))
                .build();

        Client saved = clientRepository.save(client);

        try {
            emailService.sendOtp(saved.getEmail(), saved.getFirstName(), otp);
        } catch (Exception e) {
            log.warn("Failed to send OTP email to {}: {}", saved.getEmail(), e.getMessage());
        }

        return saved;
    }

    @Transactional
    public Client verifyOtp(UUID id, String otpCode) {
        Client client = getById(id);

        if (client.getStatus() == Status.ACTIVE) {
            throw new IllegalArgumentException("Account is already active.");
        }
        if (client.getOtpCode() == null || client.getOtpExpiry() == null) {
            throw new IllegalStateException("No OTP pending for this account.");
        }
        if (Instant.now().isAfter(client.getOtpExpiry())) {
            throw new IllegalStateException("OTP has expired. Please request a new one.");
        }
        if (!client.getOtpCode().equals(otpCode)) {
            throw new IllegalArgumentException("Invalid OTP code.");
        }

        client.setStatus(Status.ACTIVE);
        client.setOtpCode(null);
        client.setOtpExpiry(null);
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

    private String generateOtp() {
        return String.format("%06d", new Random().nextInt(999999));
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
