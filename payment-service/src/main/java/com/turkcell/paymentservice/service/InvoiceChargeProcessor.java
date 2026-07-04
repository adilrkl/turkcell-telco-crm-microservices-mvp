package com.turkcell.paymentservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.turkcell.paymentservice.entity.Payment;
import com.turkcell.paymentservice.entity.PaymentAttempt;
import com.turkcell.paymentservice.repository.PaymentAttemptRepository;
import com.turkcell.paymentservice.repository.PaymentRepository;

/**
 * Tek bir fatura tahsilat denemesini kaydeder: PSP karari + payment + attempt + audit.
 * Hem ilk otomatik tahsilat ({@code chargeInvoice}) hem dunning retry ayni yolu kullanir;
 * cagiran (kendi transaction'inda) sonuca gore event yayinlar. Onaylanma karari disaridan da
 * zorlanabilir ({@code forceApprove}) — dunning'in transient toparlanma demo modu icin.
 */
@Component
public class InvoiceChargeProcessor {

    /** Bir tahsilat denemesinin sonucu. */
    public record Outcome(boolean approved, UUID paymentId) {
    }

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentGateway gateway;
    private final PaymentAuditWriter audit;

    public InvoiceChargeProcessor(PaymentRepository paymentRepository,
                                  PaymentAttemptRepository paymentAttemptRepository,
                                  PaymentGateway gateway,
                                  PaymentAuditWriter audit) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.gateway = gateway;
        this.audit = audit;
    }

    public Outcome charge(UUID invoiceId, UUID customerId, BigDecimal amount, String currency,
                          String method, boolean forceApprove) {
        boolean approved = forceApprove || gateway.authorize(amount);

        Payment payment = new Payment();
        payment.setInvoiceId(invoiceId);
        payment.setCustomerId(customerId);
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setMethod(method);
        payment.setStatus(approved ? "PAID" : "FAILED");
        if (approved) {
            payment.setPaidAt(Instant.now());
            payment.setExternalRef("PSP-" + UUID.randomUUID());
        }
        payment.setCreatedAt(Instant.now());
        paymentRepository.save(payment);

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPaymentId(payment.getId());
        attempt.setAttemptNo(1);
        attempt.setResponse(approved ? "APPROVED" : "DECLINED: tutar limiti asti");
        attempt.setAttemptedAt(Instant.now());
        paymentAttemptRepository.save(attempt);

        audit.write("Payment", payment.getId(),
                approved ? "INVOICE_CHARGE_APPROVED" : "INVOICE_CHARGE_DECLINED",
                "invoice=" + invoiceId + " amount=" + amount + " " + currency + " method=" + method);

        return new Outcome(approved, payment.getId());
    }
}
