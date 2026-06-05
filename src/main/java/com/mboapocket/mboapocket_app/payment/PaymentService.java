package com.mboapocket.mboapocket_app.payment;

import com.mboapocket.mboapocket_app.category.Category;
import com.mboapocket.mboapocket_app.category.CategoryRepository;
import com.mboapocket.mboapocket_app.expense.Expense;
import com.mboapocket.mboapocket_app.expense.ExpenseRepository;
import com.mboapocket.mboapocket_app.notification.ExpoPushService;
import com.mboapocket.mboapocket_app.payment.dto.PaymentRequest;
import com.mboapocket.mboapocket_app.payment.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final CamPayService camPayService;
    private final ExpoPushService expoPushService;

    @Value("${app.commission.rate:0.005}")
    private double commissionRate;

    public PaymentResponse initiate(Long userId, PaymentRequest req) throws Exception {
        String externalRef = "MBP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        CamPayService.CollectResult result = camPayService.collect(
                req.getPhoneFrom() != null ? req.getPhoneFrom() : "237000000000",
                req.getMontant(),
                req.getDescription() != null ? req.getDescription() : "Paiement Mboapocket",
                externalRef
        );

        BigDecimal commission = req.getMontant()
                .multiply(BigDecimal.valueOf(commissionRate))
                .setScale(2, RoundingMode.HALF_UP);

        Payment payment = Payment.builder()
                .userId(userId)
                .categoryId(req.getCategoryId())
                .montant(req.getMontant())
                .methode(req.getMethode())
                .statut(result.status())
                .reference(result.reference())
                .phoneFrom(req.getPhoneFrom())
                .merchantName(req.getMerchantName())
                .description(req.getDescription())
                .commission(commission)
                .build();

        payment = paymentRepository.save(payment);

        if ("SUCCESSFUL".equals(result.status()) && req.getCategoryId() != null) {
            createExpense(userId, req);
            notifySuccess(userId, req.getMontant(), req.getMerchantName());
        }

        return toResponse(payment);
    }

    public String checkStatus(String reference, Long userId) throws Exception {
        Payment payment = paymentRepository.findByReference(reference)
                .filter(p -> p.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Paiement introuvable"));

        if ("SUCCESSFUL".equals(payment.getStatut()) || "FAILED".equals(payment.getStatut())) {
            return payment.getStatut();
        }

        String newStatus = camPayService.checkStatus(reference);
        if (!newStatus.equals(payment.getStatut())) {
            payment.setStatut(newStatus);
            paymentRepository.save(payment);

            if ("SUCCESSFUL".equals(newStatus) && payment.getCategoryId() != null) {
                PaymentRequest req = new PaymentRequest();
                req.setCategoryId(payment.getCategoryId());
                req.setMontant(payment.getMontant());
                req.setDescription(payment.getDescription());
                createExpense(userId, req);
                notifySuccess(userId, payment.getMontant(), payment.getMerchantName());
            }
        }
        return newStatus;
    }

    // Appelé par le webhook CamPay
    public void handleWebhook(String reference, String status) {
        paymentRepository.findByReference(reference).ifPresent(payment -> {
            if ("SUCCESSFUL".equals(payment.getStatut()) || "FAILED".equals(payment.getStatut())) return;

            payment.setStatut(status);
            paymentRepository.save(payment);

            if ("SUCCESSFUL".equals(status) && payment.getCategoryId() != null) {
                PaymentRequest req = new PaymentRequest();
                req.setCategoryId(payment.getCategoryId());
                req.setMontant(payment.getMontant());
                req.setDescription(payment.getDescription());
                try { createExpense(payment.getUserId(), req); } catch (Exception ignored) {}
                notifySuccess(payment.getUserId(), payment.getMontant(), payment.getMerchantName());
            } else if ("FAILED".equals(status)) {
                notifyFailed(payment.getUserId(), payment.getMontant(), payment.getMerchantName());
            }
        });
    }

    public List<PaymentResponse> getHistory(Long userId) {
        return paymentRepository.findByUserIdOrderByDateCreationDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    private void createExpense(Long userId, PaymentRequest req) {
        Expense expense = Expense.builder()
                .userId(userId)
                .categoryId(req.getCategoryId())
                .montant(req.getMontant())
                .description(req.getDescription() != null ? req.getDescription() : "Paiement mobile")
                .date(LocalDate.now())
                .build();
        expenseRepository.save(expense);
    }

    private void notifySuccess(Long userId, BigDecimal montant, String merchant) {
        try {
            expoPushService.sendToUser(userId,
                "Paiement réussi ✅",
                montant.toPlainString() + " FCFA débités chez " + (merchant != null ? merchant : "Marchand"));
        } catch (Exception ignored) {}
    }

    private void notifyFailed(Long userId, BigDecimal montant, String merchant) {
        try {
            expoPushService.sendToUser(userId,
                "Paiement échoué ❌",
                "Le paiement de " + montant.toPlainString() + " FCFA a échoué.");
        } catch (Exception ignored) {}
    }

    private PaymentResponse toResponse(Payment p) {
        Category cat = p.getCategoryId() != null
                ? categoryRepository.findById(p.getCategoryId()).orElse(null)
                : null;
        return PaymentResponse.builder()
                .id(p.getId())
                .montant(p.getMontant())
                .methode(p.getMethode())
                .statut(p.getStatut())
                .reference(p.getReference())
                .merchantName(p.getMerchantName())
                .categoryNom(cat != null ? cat.getNom() : null)
                .categoryIcone(cat != null ? cat.getIcone() : null)
                .dateCreation(p.getDateCreation())
                .commission(p.getCommission())
                .build();
    }
}
