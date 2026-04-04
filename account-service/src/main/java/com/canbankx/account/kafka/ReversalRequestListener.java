package com.canbankx.account.kafka;

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
public class ReversalRequestListener {

    private final InterbankAccountService interbankAccountService;
    private final BankEventPublisher publisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "#{\"bank.\" + \"${bank.participant-id:participant-003}\" + \".reversal_request\"}",
            groupId = "#{\"bank-\" + \"${bank.participant-id:participant-003}\" + \"-reversal-consumer\"}")
    public void onReversalRequest(@Payload String message,
                                  @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        String paymentId = null;
        try {
            JsonNode payload = objectMapper.readTree(message);
            paymentId = getRequiredText(payload, "paymentId");
            String accountNumber = getRequiredText(payload, "senderAccountNumber");
            BigDecimal amount = getRequiredAmount(payload);
            String reversalPaymentId = paymentId + "-reversal";

            interbankAccountService.applyCredit(accountNumber, amount, reversalPaymentId);
            publisher.sendReversalConfirmed(paymentId);
            log.info("reversal CONFIRMED paymentId={} account={} amount={}", paymentId, accountNumber, amount);

        } catch (Exception e) {
            log.error("reversal request handler failed key={}: {}", key, e.getMessage(), e);
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
