package com.canbankx.identity.controller;

import com.canbankx.identity.dto.*;
import com.canbankx.identity.model.Client;
import com.canbankx.identity.service.ClientService;
import com.canbankx.identity.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/identityservice/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Auth", description = "Login and MFA verification")
public class AuthController {

    private static final Duration     CHALLENGE_TTL    = Duration.ofMinutes(5);
    private static final String       CHALLENGE_PREFIX = "mfa:challenge:";
    private static final SecureRandom SECURE_RANDOM    = new SecureRandom();

    private final ClientService       clientService;
    private final EmailService        emailService;
    private final StringRedisTemplate redisTemplate;

    @PostMapping("/login")
    @Operation(
        summary     = "Login (MFA step 1)",
        description = "Validates credentials, generates a one-time 6-digit code, and emails it to the client. "
                    + "Returns a `challengeToken` to be submitted with the OTP in the MFA step."
    )
    public ResponseEntity<MfaChallengeDTO> login(@Valid @RequestBody LoginRequestDTO dto) {
        Client client = clientService.authenticate(dto.getEmail(), dto.getPassword());

        String otp            = generateOtp();
        String challengeToken = UUID.randomUUID().toString();

        // Always log the OTP so it is available from container logs when MailHog is
        // unreachable (dev / CI environments).  Clearly prefixed so it is easy to grep.
        log.info("[MFA-OTP] client={} otp={} challengeToken={}", client.getId(), otp, challengeToken);

        // Best-effort email — a delivery failure must not block the login flow.
        // The challenge is still stored and valid; the user can read the OTP from
        // container logs (docker compose logs identity-service | grep MFA-OTP).
        try {
            emailService.sendMfaOtp(client.getEmail(), client.getFirstName(), otp, challengeToken);
        } catch (Exception ex) {
            log.warn("[MFA-OTP] Email delivery failed for client [{}] – OTP available in logs above. Cause: {}",
                    client.getId(), ex.getMessage());
        }

        // Store "clientId:otp" so both are retrieved atomically on verify
        redisTemplate.opsForValue().set(
                CHALLENGE_PREFIX + challengeToken,
                client.getId() + ":" + otp,
                CHALLENGE_TTL);

        log.info("MFA challenge issued for client [{}]", client.getId());

        return ResponseEntity.ok(new MfaChallengeDTO(
                challengeToken,
                "A 6-digit login code has been sent to " + maskEmail(client.getEmail())));
    }

    @PostMapping("/mfa")
    @Operation(
        summary     = "Verify MFA (MFA step 2)",
        description = "Validates the challenge token and the 6-digit OTP received by email. "
                    + "The token is single-use — re-login is required after a wrong OTP."
    )
    public ResponseEntity<AuthResponseDTO> verifyMfa(@Valid @RequestBody MfaVerifyDTO dto) {
        String key    = CHALLENGE_PREFIX + dto.getChallengeToken();
        String stored = redisTemplate.opsForValue().getAndDelete(key);

        if (stored == null) {
            return ResponseEntity.status(401).body(
                    new AuthResponseDTO("FAILED", "Challenge token is invalid or has expired.", null));
        }

        // stored format: "{clientId}:{otpCode}"
        String[] parts     = stored.split(":", 2);
        String   clientId  = parts[0];
        String   storedOtp = parts.length > 1 ? parts[1] : "";

        if (!storedOtp.equals(dto.getOtpCode())) {
            // Token already consumed — client must re-login to get a new OTP
            log.warn("Invalid OTP attempt for client [{}]", clientId);
            return ResponseEntity.status(401).body(
                    new AuthResponseDTO("FAILED", "Invalid OTP code. Please log in again.", null));
        }

        log.info("MFA verified for client [{}]", clientId);
        return ResponseEntity.ok(
                new AuthResponseDTO("SUCCESS", "Authentication complete.", clientId));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String generateOtp() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    /** Shows first 2 chars then masks the rest: je***@canbankx.ca */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int    at     = email.indexOf('@');
        String local  = email.substring(0, at);
        String domain = email.substring(at);
        int    show   = Math.min(2, local.length());
        return local.substring(0, show) + "*".repeat(Math.max(0, local.length() - show)) + domain;
    }
}