package com.canbankx.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * scanBasePackages = "com.canbankx" ensures Spring component-scans BOTH:
 *  - com.canbankx.payment.*          (this service's beans)
 *  - com.canbankx.common.exceptions  (GlobalExceptionHandler from the common module)
 */
@SpringBootApplication(scanBasePackages = "com.canbankx")
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.canbankx.payment.repository")
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
