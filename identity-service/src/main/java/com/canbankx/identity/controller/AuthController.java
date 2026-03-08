package com.canbankx.identity.controller;

import com.canbankx.identity.dto.*;
import com.canbankx.identity.model.Client;
import com.canbankx.identity.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/identityservice/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Auth", description = "Login and MFA verification")
public class AuthController {

    private final ClientService clientService;

    // in-memory MFA store; move to Redis for multi-instance setups
    private final Map<String, String> pendingChallenges = new ConcurrentHashMap<>();

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Validates credentials and returns an MFA challenge token")
    public ResponseEntity<MfaChallengeDTO> login(@Valid @RequestBody LoginRequestDTO dto) {
        Client client = clientService.authenticate(dto.getEmail(), dto.getPassword());

        String challengeToken = UUID.randomUUID().toString();
        pendingChallenges.put(challengeToken, client.getId().toString());

        log.info("MFA challenge issued for client [{}]", client.getId());

        return ResponseEntity.ok(new MfaChallengeDTO(
                challengeToken,
                "A 6-digit verification code has been sent to " + mask(client.getPhoneNumber())));
    }

    @PostMapping("/mfa")
    @Operation(summary = "Verify MFA", description = "Exchanges a challenge token + OTP for an auth confirmation")
    public ResponseEntity<AuthResponseDTO> verifyMfa(@Valid @RequestBody MfaVerifyDTO dto) {
        String clientId = pendingChallenges.remove(dto.getChallengeToken());

        if (clientId == null) {
            return ResponseEntity.status(401).body(
                    new AuthResponseDTO("FAILED", "Invalid or expired challenge token.", null));
        }

        if (!dto.getOtpCode().matches("\\d{6}")) {
            return ResponseEntity.status(401).body(
                    new AuthResponseDTO("FAILED", "Invalid OTP format.", null));
        }

        log.info("MFA verified for client [{}]", clientId);
        return ResponseEntity.ok(
                new AuthResponseDTO("SUCCESS", "Authentication complete.", clientId));
    }

    private String mask(String phone) {
        if (phone == null || phone.length() < 4) return "****";
        return "*".repeat(phone.length() - 2) + phone.substring(phone.length() - 2);
    }
}