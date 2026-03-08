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
}
