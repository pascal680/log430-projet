package com.canbankx.account.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Slf4j
public class BankEventPublisher {

    private static final String STATUS_CONFIRMED = "CONFIRMED";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String participantId;

    public BankEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                              ObjectMapper objectMapper,
                              @Value("${bank.participant-id:participant-003}") String participantId) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.participantId = participantId;
    }

    public void sendDebitResponse(String paymentId, String status, String reason) {
        String topic = topic("debit_response");
        kafkaTemplate.send(topic, paymentId, buildResponsePayload(paymentId, status, reason));
        log.info("debit_response sent topic={} paymentId={} status={}", topic, paymentId, status);
    }

    public void sendCreditResponse(String paymentId, String status, String reason) {
        String topic = topic("credit_response");
        kafkaTemplate.send(topic, paymentId, buildResponsePayload(paymentId, status, reason));
        log.info("credit_response sent topic={} paymentId={} status={}", topic, paymentId, status);
    }

    public void sendReversalConfirmed(String paymentId) {
        String topic = topic("reversal_confirmed");
        kafkaTemplate.send(topic, paymentId, buildResponsePayload(paymentId, STATUS_CONFIRMED, null));
        log.info("reversal_confirmed sent topic={} paymentId={}", topic, paymentId);
    }

    private String topic(String suffix) {
        return "bank." + participantId + "." + suffix;
    }

    private String buildResponsePayload(String paymentId, String status, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentId", paymentId);
        payload.put("status", status);
        payload.put("reason", reason);
        payload.put("processedAt", Instant.now().toString());

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize bank event response", e);
        }
    }
}
