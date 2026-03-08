package com.canbankx.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendOtp(String toEmail, String firstName, String otpCode) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("no-reply@canbankx.ca");
        message.setTo(toEmail);
        message.setSubject("CanBankX – Verify your account");
        message.setText(
            "Hello " + firstName + ",\n\n" +
            "Your verification code is: " + otpCode + "\n\n" +
            "This code expires in 10 minutes.\n\n" +
            "If you did not register, ignore this email.\n\n" +
            "– CanBankX Team"
        );
        mailSender.send(message);
        log.info("OTP email sent to {}", toEmail);
    }

    /**
     * Sends the MFA login OTP.
     * The challengeToken is embedded in the body so automated tests can
     * correlate the email to the exact login request (no race condition
     * when multiple VUs log in concurrently with the same account).
     */
    public void sendMfaOtp(String toEmail, String firstName, String otpCode, String challengeToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("no-reply@canbankx.ca");
        message.setTo(toEmail);
        message.setSubject("CanBankX – Your login code");
        message.setText(
            "Hello " + firstName + ",\n\n" +
            "Your login code is: " + otpCode + "\n\n" +
            "This code expires in 5 minutes.\n" +
            "Do not share this code with anyone.\n\n" +
            "If you did not request this, please secure your account immediately.\n\n" +
            "Reference: " + challengeToken + "\n\n" +
            "– CanBankX Team"
        );
        mailSender.send(message);
        log.info("MFA OTP email sent to {}", toEmail);
    }
}
