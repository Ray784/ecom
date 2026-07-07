package com.ecommerce.payment.dto;

import com.ecommerce.payment.model.PaymentTransaction;
import java.math.BigDecimal;

/** External view of a payment transaction. */
public record PaymentTransactionResponse(
    String transactionId,
    String orderId,
    String userId,
    BigDecimal amount,
    String gatewayRef,
    String status,
    String failureReason,
    String createdAt) {

  public static PaymentTransactionResponse from(PaymentTransaction tx) {
    return new PaymentTransactionResponse(
        tx.getId(),
        tx.getOrderId(),
        tx.getUserId(),
        tx.getAmount(),
        tx.getGatewayRef(),
        tx.getStatus(),
        tx.getFailureReason(),
        tx.getCreatedAt() == null ? null : tx.getCreatedAt().toString());
  }
}
