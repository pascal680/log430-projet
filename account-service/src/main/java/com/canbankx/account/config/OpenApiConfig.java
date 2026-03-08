package com.canbankx.account.config;

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

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CanBankX – Account Service")
                        .description("""
                                **UC-03 Account Management** – open accounts (CHECKING / SAVINGS), query balances.
                                **UC-04 Balance Operations** – internal debit/credit used by the payment service.
                                
                                All endpoints require an `Authorization: Bearer <token>` header.
                                Click **Authorize** and paste your token (without the `Bearer ` prefix).
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("CanBankX Platform Team")))
                .servers(List.of(
                        new Server().url("http://localhost:8082").description("Direct (dev)"),
                        new Server().url("http://localhost:8080").description("Via KrakenD Gateway")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME,
                                new SecurityScheme()
                                        .name(BEARER_SCHEME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("UUID")
                                        .description("Paste the token returned by the MFA endpoint")));
    }
}
