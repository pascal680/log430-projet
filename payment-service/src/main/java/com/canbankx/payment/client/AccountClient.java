package com.canbankx.payment.client;

import com.canbankx.payment.dto.AccountInfoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Component
@Slf4j
public class AccountClient {

    /**
     * Shared HTTP/1.1 client with built-in connection pooling.
     * Replaces SimpleClientHttpRequestFactory (which creates a new TCP connection
     * per request) with persistent keep-alive connections, eliminating TCP
     * handshake overhead under concurrent load.
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final RestClient restClient;

    public AccountClient(
            @Value("${services.account.url:http://account-service:8082}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(HTTP_CLIENT))
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

    public AccountInfoDTO getAccountByNumber(String accountNumber) {
        log.debug("Fetching account info for account number [{}]", accountNumber);
        return restClient.get()
                .uri("/accountservice/accounts/number/{n}", accountNumber)
                .retrieve()
                .body(AccountInfoDTO.class);
    }
}