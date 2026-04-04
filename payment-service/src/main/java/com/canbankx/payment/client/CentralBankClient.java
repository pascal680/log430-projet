package com.canbankx.payment.client;

import com.canbankx.payment.dto.InterbankPaymentRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Component
@Slf4j
public class CentralBankClient {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final RestClient restClient;

    public CentralBankClient(
            @Value("${services.central-bank.url:http://localhost:18080}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(HTTP_CLIENT))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Map<String, Object> initiatePayment(String participantId, InterbankPaymentRequestDTO request) {
        log.info("Submitting interbank payment via central bank for participant={} sender={} recipient={} amount={}",
                participantId, request.getSenderAccountNumber(), request.getRecipientAccountNumber(), request.getAmount());

        return restClient.post()
                .uri("/v1/payments")
                .header("X-Participant-Id", participantId)
                .header("X-Idempotency-Key", request.getIdempotencyKey())
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}
