package com.canbankx.payment.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME  = "bearerAuth";
    private static final String IDEM_KEY_SCHEME = "idempotencyKey";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CanBankX – Payment Service")
                        .description("""
                                **UC-05 Payment Processing** – DEBIT, CREDIT, and TRANSFER transactions.
                                
                                **POST /transactions** additionally requires an `Idempotency-Key` header
                                (any UUID) to prevent duplicate submissions.
                                
                                Click **Authorize** and fill in both the bearer token and the idempotency key.
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("CanBankX Platform Team")))
                .servers(List.of(
                        new Server().url("http://localhost:8083").description("Direct (dev)"),
                        new Server().url("http://localhost:8080").description("Via KrakenD Gateway")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(BEARER_SCHEME)
                        .addList(IDEM_KEY_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME,
                                new SecurityScheme()
                                        .name(BEARER_SCHEME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("UUID")
                                        .description("Token returned by the MFA endpoint"))
                        .addSecuritySchemes(IDEM_KEY_SCHEME,
                                new SecurityScheme()
                                        .name("Idempotency-Key")
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .description("Any UUID — reuse the same key to safely retry a transaction")));
    }
}
