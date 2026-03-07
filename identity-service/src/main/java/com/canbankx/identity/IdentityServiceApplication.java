package com.canbankx.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * scanBasePackages = "com.canbankx" ensures Spring component-scans BOTH:
 *  - com.canbankx.identity.*         (this service's beans)
 *  - com.canbankx.common.exceptions  (GlobalExceptionHandler from the common module)
 */
@SpringBootApplication(scanBasePackages = "com.canbankx")
@EnableJpaAuditing
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
