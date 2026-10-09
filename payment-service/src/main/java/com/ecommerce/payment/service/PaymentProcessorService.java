package com.ecommerce.payment.service;

import com.ecommerce.common.dto.ItemDetails;
import com.ecommerce.common.dto.OrderPlacedEvent;
import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.payment.gateway.GatewayErrorMapper;
import com.ecommerce.payment.gateway.PaymentGatewayResponse;
import com.ecommerce.payment.gateway.SimulatedPaymentGateway;
import com.ecommerce.payment.gateway.SimulatedPaymentGateway.GatewayCommunicationException;
import com.ecommerce.payment.model.PaymentTransaction;
import com.ecommerce.payment.repository.PaymentTransactionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a payment from the OrderPlaced event to the published PaymentResult event.
 *
 * <p>Every attempt is persisted before the gateway is contacted. A failed attempt is stored as
 * FAILED, published to the failure topic so the order saga can react, and then raised as the
 * matching catalog exception so REST callers receive the proper error code.
 */
@Service
public class PaymentProcessorService {

  private static final Logger log = LoggerFactory.getLogger(PaymentProcessorService.class);

  private final PaymentTransactionRepository repository;
  private final SimulatedPaymentGateway gateway;
  private final GatewayErrorMapper gatewayErrorMapper;
  private final KafkaProducerService producer;

  public PaymentProcessorService(
      PaymentTransactionRepository repository,
      SimulatedPaymentGateway gateway,
      GatewayErrorMapper gatewayErrorMapper,
      KafkaProducerService producer) {
    this.repository = repository;
    this.gateway = gateway;
    this.gatewayErrorMapper = gatewayErrorMapper;
    this.producer = producer;
  }

  /**
   * Charges the order and publishes the result.
   *
   * @return the transaction in SUCCESS status
   */
  public PaymentTransaction initiatePayment(OrderPlacedEvent event) {
    validate(event);
    repository
        .findFirstByOrderIdOrderByCreatedAtDesc(event.getOrderId())
        .ifPresent(
            existing -> {
              throw CodedErrorFactory.create(
                  MappedError.PAY_4003, existing.getId(), existing.getStatus());
            });

    PaymentTransaction transaction =
        new PaymentTransaction(
            "TXN-" + UUID.randomUUID(),
            event.getOrderId(),
            event.getUserId(),
            event.getTotalAmount(),
            null,
            PaymentTransaction.STATUS_INITIATED);
    repository.save(transaction);
    log.info("Initiated payment {} for order {}", transaction.getId(), transaction.getOrderId());

    if (isFraudSuspect(event.getUserId())) {
      throw failAndPublish(
          transaction, CodedErrorFactory.create(MappedError.PAY_4006, event.getUserId()));
    }

    PaymentGatewayResponse response;
    try {
      response =
          gateway.charge(
              transaction.getId(), event.getOrderId(), event.getUserId(), event.getTotalAmount());
    } catch (GatewayCommunicationException ex) {
      throw failAndPublish(
          transaction, CodedErrorFactory.create(MappedError.PAY_5002, ex, transaction.getId()));
    }
    transaction.setGatewayRef(response.gatewayRef());
    transaction.setRazorpayOrderId(response.razorpayOrderId());
    repository.save(transaction);

    if (response.approved()) {
      return handleGatewayCallback(transaction.getId(), true, null);
    }
    throw failAndPublish(transaction, gatewayFailure(response.gatewayCode(), transaction));
  }

  /**
   * Applies the gateway verdict to an INITIATED transaction and publishes the outcome.
   *
   * @return the updated transaction
   */
  public PaymentTransaction handleGatewayCallback(
      String transactionId, boolean approved, String reason) {
    PaymentTransaction transaction =
        repository
            .findById(transactionId)
            .orElseThrow(() -> CodedErrorFactory.create(MappedError.PAY_4004, transactionId));
    if (transaction.isTerminal()) {
      throw CodedErrorFactory.create(MappedError.PAY_4003, transactionId, transaction.getStatus());
    }
    transaction.setStatus(
        approved ? PaymentTransaction.STATUS_SUCCESS : PaymentTransaction.STATUS_FAILED);
    transaction.setFailureReason(approved ? null : reason);
    repository.save(transaction);
    producer.sendPaymentResult(
        new PaymentResultEvent(
            transaction.getOrderId(), transaction.getId(), approved, transaction.getFailureReason()));
    log.info("Payment {} for order {} finished with {}",
        transactionId, transaction.getOrderId(), transaction.getStatus());
    return transaction;
  }

  /** Returns the latest transaction of the order or throws PAY_4004. */
  public PaymentTransaction getByOrderId(String orderId) {
    return repository
        .findFirstByOrderIdOrderByCreatedAtDesc(orderId)
        .orElseThrow(() -> CodedErrorFactory.create(MappedError.PAY_4004, orderId));
  }

  private ApplicationBaseException failAndPublish(
      PaymentTransaction transaction, ApplicationBaseException error) {
    handleGatewayCallback(
        transaction.getId(), false, error.getErrorCode() + ": " + error.getMessage());
    return error;
  }

  private ApplicationBaseException gatewayFailure(String gatewayCode, PaymentTransaction tx) {
    MappedError mapped = gatewayErrorMapper.toMappedError(gatewayCode);
    return switch (mapped) {
      case PAY_4001 -> CodedErrorFactory.create(mapped, tx.getOrderId());
      case PAY_4006 -> CodedErrorFactory.create(mapped, tx.getUserId());
      case PAY_5002 -> CodedErrorFactory.create(mapped, tx.getId());
      case PAY_5004 -> CodedErrorFactory.create(mapped, gatewayCode);
      default -> CodedErrorFactory.create(mapped);
    };
  }

  private static boolean isFraudSuspect(String userId) {
    return userId.toLowerCase(Locale.ROOT).contains("fraud");
  }

  private static void validate(OrderPlacedEvent event) {
    if (event == null || isBlank(event.getOrderId()) || isBlank(event.getUserId())) {
      throw CodedErrorFactory.create(MappedError.PAY_4002);
    }
    BigDecimal total = event.getTotalAmount();
    if (total == null || total.signum() <= 0) {
      throw CodedErrorFactory.create(MappedError.PAY_4005, total);
    }
    BigDecimal itemsTotal = sumItems(event.getItems());
    if (itemsTotal != null && itemsTotal.compareTo(total) != 0) {
      throw CodedErrorFactory.create(MappedError.PAY_4005, total);
    }
  }

  /** Returns the priced sum of the items, or null when any line lacks a price or quantity. */
  private static BigDecimal sumItems(List<ItemDetails> items) {
    if (items == null || items.isEmpty()) {
      return null;
    }
    BigDecimal sum = BigDecimal.ZERO;
    for (ItemDetails item : items) {
      if (item == null || item.getUnitPrice() == null || item.getQuantity() == null) {
        return null;
      }
      sum = sum.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
    }
    return sum;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
