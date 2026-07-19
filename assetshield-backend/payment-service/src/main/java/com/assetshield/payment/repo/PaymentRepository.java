package com.assetshield.payment.repo;

import com.assetshield.payment.domain.Payment;
import com.assetshield.payment.domain.PaymentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByProviderReference(String providerReference);

    /** SUCCESS rows whose downstream handler never acknowledged — reconciler input. */
    List<Payment> findByStatusAndDispatchedAtIsNull(PaymentStatus status);

    /** Billing history, newest first. */
    org.springframework.data.domain.Page<Payment> findByUserIdOrderByCreatedAtDesc(
            UUID userId, org.springframework.data.domain.Pageable pageable);
}
