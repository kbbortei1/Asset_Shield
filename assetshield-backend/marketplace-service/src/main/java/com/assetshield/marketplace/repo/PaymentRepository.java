package com.assetshield.marketplace.repo;

import com.assetshield.marketplace.domain.Payment;
import com.assetshield.marketplace.domain.PaymentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByProviderReference(String providerReference);

    /** SUCCESS rows whose downstream handler never acknowledged — reconciler input. */
    List<Payment> findByStatusAndDispatchedAtIsNull(PaymentStatus status);
}
