package com.ecommerce.payment.repository;

import com.ecommerce.payment.model.PaymentTransaction;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Persistence for payment transactions. */
@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, String> {

  /** Returns the most recent transaction recorded for the order. */
  Optional<PaymentTransaction> findFirstByOrderIdOrderByCreatedAtDesc(String orderId);
}
