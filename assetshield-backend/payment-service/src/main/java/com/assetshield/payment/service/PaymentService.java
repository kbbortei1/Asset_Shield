package com.assetshield.payment.service;

import com.assetshield.payment.provider.PaymentProvider;
import com.assetshield.payment.common.ApiException;
import com.assetshield.payment.common.ErrorCode;
import com.assetshield.payment.domain.Payment;
import com.assetshield.payment.domain.PaymentPurpose;
import com.assetshield.payment.repo.PaymentRepository;
import com.assetshield.payment.web.dto.PaymentDtos.InitializeRequest;
import com.assetshield.payment.web.dto.PaymentDtos.InitializeResponse;
import com.assetshield.payment.web.dto.PaymentDtos.PaymentDetails;
import com.assetshield.payment.web.dto.PaymentDtos.VerifyResponse;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final PaymentSettlementService settlementService;
    private final SecureRandom random = new SecureRandom();

    public PaymentService(PaymentRepository paymentRepository, PaymentProvider paymentProvider,
                          PaymentSettlementService settlementService) {
        this.paymentRepository = paymentRepository;
        this.paymentProvider = paymentProvider;
        this.settlementService = settlementService;
    }

    /** Internal entry point: creates the INITIATED row, then opens the checkout. */
    @Transactional
    public InitializeResponse initialize(InitializeRequest request) {
        Payment payment = new Payment();
        payment.setUserId(request.userId());
        payment.setPurpose(request.purpose());
        payment.setAmount(request.amountGhs());
        payment.setReferenceEntityId(request.referenceEntityId());
        payment.setProviderReference(newReference(request.purpose()));
        // flush first: the mock provider's auto-settle must find the row
        Payment saved = paymentRepository.saveAndFlush(payment);

        PaymentProvider.InitResult init = paymentProvider.initialize(
                saved.getProviderReference(), saved.getAmount(),
                synthesizeEmail(request.userPhone()),
                Map.of("purpose", saved.getPurpose().name(),
                        "referenceEntityId", saved.getReferenceEntityId().toString(),
                        "userId", saved.getUserId().toString()));
        return new InitializeResponse(saved.getId(), saved.getProviderReference(),
                init.authorizationUrl());
    }

    /** Post-checkout confirmation (and the dev fallback when webhooks can't reach us). */
    @Transactional
    public VerifyResponse verify(UUID userId, String reference) {
        Payment payment = requireOwnPayment(userId, reference);
        PaymentProvider.VerifyResult result = paymentProvider.verify(reference);
        if (result.status() == PaymentProvider.VerifyStatus.SUCCESS) {
            settlementService.settle(reference, result.raw());
        }
        Payment refreshed = paymentRepository.findByProviderReference(reference).orElseThrow();
        return new VerifyResponse(reference, refreshed.getStatus().name(),
                refreshed.getPurpose().name());
    }

    /** The payer's billing history, newest first. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.assetshield.payment.web.dto.PaymentDtos.PaymentSummary> history(
            UUID userId, int page, int size) {
        return paymentRepository
                .findByUserIdOrderByCreatedAtDesc(userId, org.springframework.data.domain.PageRequest.of(page, size))
                .map(p -> new com.assetshield.payment.web.dto.PaymentDtos.PaymentSummary(
                        p.getProviderReference(), p.getPurpose().name(), p.getAmount(),
                        p.getCurrency(), p.getStatus().name(), p.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public PaymentDetails details(UUID userId, String reference) {
        Payment payment = requireOwnPayment(userId, reference);
        return new PaymentDetails(payment.getProviderReference(), payment.getPurpose().name(),
                payment.getAmount(), payment.getCurrency(), payment.getStatus().name(),
                payment.getCreatedAt());
    }

    /** Payer mismatch is a 404, not a 403 — payments must not be enumerable. */
    private Payment requireOwnPayment(UUID userId, String reference) {
        return paymentRepository.findByProviderReference(reference)
                .filter(payment -> payment.getUserId().equals(userId))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Payment not found"));
    }

    /** ASGH-&lt;purpose-prefix&gt;-&lt;12 hex&gt;, e.g. ASGH-DSR-a3f19c0b44de. */
    private String newReference(PaymentPurpose purpose) {
        byte[] bytes = new byte[6];
        random.nextBytes(bytes);
        return "ASGH-" + purpose.referencePrefix() + "-" + HexFormat.of().formatHex(bytes);
    }

    /** Paystack requires an email; users register by phone. */
    public static String synthesizeEmail(String phone) {
        return phone.replaceAll("\\D", "") + "@assetshield.app";
    }
}
