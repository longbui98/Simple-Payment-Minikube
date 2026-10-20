package com.example.processor.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
@RestController
public class PaymentApiApplication {
    private static final Logger log = LoggerFactory.getLogger(PaymentApiApplication.class);
    private final RestClient processorClient;

    public PaymentApiApplication(
            @org.springframework.beans.factory.annotation.Value(
                    "${processor.url:http://payment-processor}")
            String processorUrl) {
        this.processorClient = RestClient.builder()
                .baseUrl(processorUrl)
                .requestInterceptor((request, body, execution) -> {
                    String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
                    if (traceId != null) {
                        request.getHeaders().add(TraceIdFilter.TRACE_ID_HEADER, traceId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    @PostMapping("/payments")
    public ResponseEntity<?> createPayment(
            @RequestBody PaymentRequest request) {

        log.info("Start process payment");
        return processorClient.post()
                .uri("/process")
                .body(request)
                .retrieve()
                .toEntity(Object.class);
    }

    @PostMapping("/wallets")
    public ResponseEntity<?> createWallet(
            @RequestBody WalletRequest request) {

        log.info("Start process create wallet");
        return processorClient.post()
                .uri("/wallets")
                .body(request)
                .retrieve()
                .toEntity(Object.class);
    }

    public record PaymentRequest(
            String paymentId, BigDecimal amount, String currency, String fromUsrId, String toUsrId) {}

    public record WalletRequest(String userId, BigDecimal balance) {}

    public static void main(String[] args) {
        SpringApplication.run(
                PaymentApiApplication.class, args);
    }
}
