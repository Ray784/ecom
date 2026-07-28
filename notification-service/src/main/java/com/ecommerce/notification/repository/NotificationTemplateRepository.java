package com.ecommerce.notification.repository;

import com.ecommerce.notification.model.NotificationLog;
import com.ecommerce.notification.model.NotificationTemplate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** In memory template store seeded with the default order and payment templates. */
@Repository
public class NotificationTemplateRepository {

  private final Map<String, NotificationTemplate> templates = new ConcurrentHashMap<>();

  public NotificationTemplateRepository() {
    save(
        new NotificationTemplate(
            NotificationTemplate.ORDER_PLACED_TEMPLATE,
            NotificationLog.TYPE_EMAIL,
            "Hello {userId}, your order {orderId} for {totalAmount} has been placed successfully."));
    save(
        new NotificationTemplate(
            NotificationTemplate.PAYMENT_SUCCESS_TEMPLATE,
            NotificationLog.TYPE_EMAIL,
            "Payment received for order {orderId}. Transaction {transactionId} completed successfully."));
    save(
        new NotificationTemplate(
            NotificationTemplate.PAYMENT_FAILED_TEMPLATE,
            NotificationLog.TYPE_EMAIL,
            "Payment for order {orderId} failed: {failureReason}"));
  }

  public Optional<NotificationTemplate> findById(String templateId) {
    return templateId == null ? Optional.empty() : Optional.ofNullable(templates.get(templateId));
  }

  public NotificationTemplate save(NotificationTemplate template) {
    templates.put(template.getTemplateId(), template);
    return template;
  }
}
