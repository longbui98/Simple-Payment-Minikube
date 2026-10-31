package com.example.processor.controller;

import com.example.processor.data.Payment;
import com.example.processor.dto.PaymentRequest;
import com.example.processor.dto.PaymentResponse;
import com.example.processor.dto.PaymentResponseError;
import com.example.processor.dto.WalletRequest;
import com.example.processor.dto.WalletResponse;
import com.example.processor.service.PaymentService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
public class ProcessorController {
    private static final Logger log = LoggerFactory.getLogger(ProcessorController.class);
    private static final int MAX_REQUESTS = 5;
    private static final Duration CACHE_TTL = Duration.ofSeconds(15);
    private final PaymentService paymentService;
    private final StringRedisTemplate redisTemplate;

    public ProcessorController(PaymentService paymentService, StringRedisTemplate redisTemplate) {
        this.paymentService = paymentService;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("/process")
    public ResponseEntity<?> process(@RequestBody PaymentRequest request, HttpServletRequest httpServletRequest) {
        String traceId = httpServletRequest.getHeader("X-Trace-Id");
        try {
            String key = senderIdKey(request.fromUsrId());

            Long count = redisTemplate.opsForValue().increment(key);

            if (count != null && count == 1) {
                redisTemplate.expire(key, CACHE_TTL);
            }

            if (count != null && count > MAX_REQUESTS) {
                log.error("User Id {} exceeded maximum transactions in 15 seconds", request.fromUsrId());
                throw new Exception("Maximum request transactions exceeded for userId " + request.fromUsrId());
            }
            Payment payment = paymentService.processPayment(request, traceId);
            return ResponseEntity.ok(new PaymentResponse(
                    payment.getId(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getStatus(),
                    payment.getFromUsrId(),
                    payment.getToUsrId()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.ok(new PaymentResponseError(traceId, e.getMessage()));
        } catch (Exception e) {
            log.error("Error while processing payment record: {}", e.getMessage());
            return ResponseEntity.ok(new PaymentResponseError(traceId, e.getMessage()));
        }
    }

    @PostMapping("/wallets")
    public ResponseEntity<WalletResponse> createWallet(@RequestBody WalletRequest request) {
        WalletResponse response = paymentService.createWallet(request);
        return ResponseEntity.ok(response);
    }

    private String senderIdKey(String userId) {
        return "userId:" + userId;
    }
}