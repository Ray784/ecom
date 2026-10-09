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

  /** Returns the most recent transaction carrying the Razorpay pay_ reference. */
  Optional<PaymentTransaction> findFirstByGatewayRefOrderByCreatedAtDesc(String gatewayRef);

  /** Returns the most recent transaction attempted on the Razorpay order_ ID. */
  Optional<PaymentTransaction> findFirstByRazorpayOrderIdOrderByCreatedAtDesc(
      String razorpayOrderId);
}
