package com.example.processor.utils;

import com.example.processor.dto.PaymentRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class ValidatePaymentRequest {
    private static final Logger log = LoggerFactory.getLogger(ValidatePaymentRequest.class);

    public static void validateRequest(PaymentRequest request, String traceId) throws Exception {
        if (Objects.isNull(request.fromUsrId())) {
            log.error("Trace-ID: {} Error missing request fromUsrId: ", traceId);
            throw new Exception("Error missing request fromUsrId");
        }
        if (Objects.isNull(request.toUsrId())) {
            throw new Exception("Error missing request toUsrId");
        }
    }
}
