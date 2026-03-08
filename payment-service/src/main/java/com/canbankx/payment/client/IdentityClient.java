package com.canbankx.payment.client;

import com.canbankx.payment.dto.ClientInfoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

@Component
@Slf4j
public class IdentityClient {

    /** Shared HTTP/1.1 client with connection pooling – same rationale as AccountClient. */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final RestClient restClient;

    public IdentityClient(
            @Value("${services.identity.url:http://identity-service:8081}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(HTTP_CLIENT))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public ClientInfoDTO getClientById(UUID clientId) {
        log.debug("Fetching client info for clientId [{}]", clientId);
        return restClient.get()
                .uri("/identityservice/clients/{id}", clientId)
                .retrieve()
                .body(ClientInfoDTO.class);
    }
}