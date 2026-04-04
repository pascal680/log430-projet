package com.canbankx.account.kafka;

import com.canbankx.account.exceptions.AccountNotFoundException;
import com.canbankx.account.service.InterbankAccountService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreditRequestListener {

    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String REASON_ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";
    private static final String REASON_INTERNAL_ERROR = "INTERNAL_ERROR";

    private final InterbankAccountService interbankAccountService;
    private final BankEventPublisher publisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "#{\"bank.\" + \"${bank.participant-id:participant-003}\" + \".credit_request\"}",
            groupId = "#{\"bank-\" + \"${bank.participant-id:participant-003}\" + \"-credit-consumer\"}")
    public void onCreditRequest(@Payload String message,
                                @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        String paymentId = null;
        try {
            JsonNode payload = objectMapper.readTree(message);
            paymentId = getRequiredText(payload, "paymentId");
            String accountNumber = getRequiredText(payload, "recipientAccountNumber");
            BigDecimal amount = getRequiredAmount(payload);

            interbankAccountService.applyCredit(accountNumber, amount, paymentId);
            publisher.sendCreditResponse(paymentId, STATUS_CONFIRMED, null);
            log.info("credit CONFIRMED paymentId={} account={} amount={}", paymentId, accountNumber, amount);

        } catch (AccountNotFoundException e) {
            publisher.sendCreditResponse(paymentId, STATUS_REJECTED, REASON_ACCOUNT_NOT_FOUND);
            log.warn("credit REJECTED paymentId={}: {}", paymentId, e.getMessage());
        } catch (Exception e) {
            log.error("credit request handler failed key={}: {}", key, e.getMessage(), e);
            if (paymentId != null) {
                publisher.sendCreditResponse(paymentId, STATUS_REJECTED, REASON_INTERNAL_ERROR);
            }
        }
    }

    private String getRequiredText(JsonNode payload, String fieldName) {
        JsonNode value = payload.get(fieldName);
        if (value == null || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
        return value.asText();
    }

    private BigDecimal getRequiredAmount(JsonNode payload) {
        return new BigDecimal(getRequiredText(payload, "amount"));
    }
}
