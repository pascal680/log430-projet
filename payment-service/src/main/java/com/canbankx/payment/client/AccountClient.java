package com.canbankx.payment.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
@Slf4j
public class AccountClient {

    private final RestClient restClient;

    public AccountClient(
            @Value("${services.account.url:http://account-service:8082}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public void debit(String accountNumber, BigDecimal amount, String transactionRef) {
        log.info("Debiting {} from account [{}] (txRef={})", amount, accountNumber, transactionRef);
        restClient.patch()
                .uri("/accountservice/accounts/number/{n}/debit", accountNumber)
                .body(Map.of("amount", amount, "transactionRef", transactionRef))
                .retrieve()
                .toBodilessEntity();
    }

    public void credit(String accountNumber, BigDecimal amount, String transactionRef) {
        log.info("Crediting {} to account [{}] (txRef={})", amount, accountNumber, transactionRef);
        restClient.patch()
                .uri("/accountservice/accounts/number/{n}/credit", accountNumber)
                .body(Map.of("amount", amount, "transactionRef", transactionRef))
                .retrieve()
                .toBodilessEntity();
    }
}
