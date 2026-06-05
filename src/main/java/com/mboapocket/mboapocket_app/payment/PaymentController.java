package com.mboapocket.mboapocket_app.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mboapocket.mboapocket_app.payment.dto.PaymentRequest;
import com.mboapocket.mboapocket_app.payment.dto.PaymentResponse;
import com.mboapocket.mboapocket_app.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${campay.webhook-secret:}")
    private String webhookSecret;

    @PostMapping("/initiate")
    ResponseEntity<PaymentResponse> initiate(@AuthenticationPrincipal User user,
                                             @RequestBody PaymentRequest req) {
        try {
            return ResponseEntity.ok(paymentService.initiate(user.getId(), req));
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @GetMapping("/status/{reference}")
    ResponseEntity<Map<String, String>> status(@AuthenticationPrincipal User user,
                                               @PathVariable String reference) {
        try {
            String status = paymentService.checkStatus(reference, user.getId());
            return ResponseEntity.ok(Map.of("status", status));
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @GetMapping("/history")
    ResponseEntity<List<PaymentResponse>> history(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(paymentService.getHistory(user.getId()));
    }

    // Webhook CamPay — appelé par CamPay quand un paiement est confirmé
    @PostMapping("/webhook/campay")
    ResponseEntity<Void> webhook(@RequestHeader(value = "X-Webhook-Secret", required = false) String secret,
                                 @RequestBody String body) {
        try {
            // Vérifier le secret si configuré
            if (!webhookSecret.isBlank() && !webhookSecret.equals(secret)) {
                return ResponseEntity.status(401).build();
            }
            JsonNode node = mapper.readTree(body);
            String reference = node.path("reference").asText();
            String status    = node.path("status").asText();
            if (!reference.isBlank() && !status.isBlank()) {
                paymentService.handleWebhook(reference, status);
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<Map<String, String>> handleError(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Erreur paiement"));
    }
}
