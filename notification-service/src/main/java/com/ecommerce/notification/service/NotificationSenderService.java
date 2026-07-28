package com.ecommerce.notification.service;

import com.ecommerce.common.dto.OrderPlacedEvent;
import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.notification.model.NotificationLog;
import com.ecommerce.notification.model.NotificationTemplate;
import com.ecommerce.notification.repository.NotificationLogRepository;
import com.ecommerce.notification.repository.NotificationTemplateRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends email and SMS notifications through simulated providers and keeps a log of every attempt.
 *
 * <p>The providers are simulated so the platform has no external dependency. Recipients that
 * contain the marker smtp-down make the email provider fail, and phone numbers starting with +000
 * make the SMS gateway time out, which lets tests reach NOTIF_5001 and NOTIF_5002.
 */
@Service
public class NotificationSenderService {

  static final String EMAIL_FAILURE_MARKER = "smtp-down";
  static final String SMS_TIMEOUT_PREFIX = "+000";
  static final String CUSTOMER_EMAIL_DOMAIN = "@customers.ecommerce.local";
  static final String DIRECT_SMS_TEMPLATE = "SMS_DIRECT";

  private static final Logger log = LoggerFactory.getLogger(NotificationSenderService.class);
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)\\}");

  private final NotificationLogRepository logRepository;
  private final NotificationTemplateRepository templateRepository;

  public NotificationSenderService(
      NotificationLogRepository logRepository, NotificationTemplateRepository templateRepository) {
    this.logRepository = logRepository;
    this.templateRepository = templateRepository;
  }

  /**
   * Renders the template with the data and emails it to the recipient.
   *
   * <p>The optional data keys userId and orderId are copied onto the log entry. Without userId
   * the recipient address is used as the user key.
   */
  public NotificationLog sendEmail(String recipient, String templateId, Map<String, Object> data) {
    if (isBlank(recipient)) {
      throw CodedErrorFactory.create(MappedError.NOTIF_4001, NotificationLog.TYPE_EMAIL);
    }
    NotificationTemplate template =
        templateRepository
            .findById(templateId)
            .orElseThrow(() -> CodedErrorFactory.create(MappedError.NOTIF_4002, templateId));
    Map<String, Object> values = data == null ? Map.of() : data;
    String message = render(template, values);
    NotificationLog entry =
        newLog(
            stringOr(values.get("userId"), recipient),
            stringOr(values.get("orderId"), null),
            NotificationLog.TYPE_EMAIL,
            templateId,
            message);
    if (recipient.contains(EMAIL_FAILURE_MARKER)) {
      entry.setStatus(NotificationLog.STATUS_FAILED);
      persist(entry);
      throw CodedErrorFactory.create(MappedError.NOTIF_5001);
    }
    persist(entry);
    log.info("Email {} sent to {} for order {}", templateId, recipient, entry.getOrderId());
    return entry;
  }

  /** Sends a free text SMS. */
  public NotificationLog sendSms(String phoneNumber, String message) {
    return sendSms(phoneNumber, message, null, null);
  }

  /** Sends a free text SMS and links the log entry to the user and order when given. */
  public NotificationLog sendSms(String phoneNumber, String message, String userId, String orderId) {
    if (isBlank(phoneNumber)) {
      throw CodedErrorFactory.create(MappedError.NOTIF_4001, NotificationLog.TYPE_SMS);
    }
    if (isBlank(message)) {
      throw CodedErrorFactory.create(MappedError.NOTIF_4003, "message", DIRECT_SMS_TEMPLATE);
    }
    NotificationLog entry =
        newLog(
            isBlank(userId) ? phoneNumber : userId,
            orderId,
            NotificationLog.TYPE_SMS,
            DIRECT_SMS_TEMPLATE,
            message);
    if (phoneNumber.startsWith(SMS_TIMEOUT_PREFIX)) {
      entry.setStatus(NotificationLog.STATUS_FAILED);
      persist(entry);
      throw CodedErrorFactory.create(MappedError.NOTIF_5002);
    }
    persist(entry);
    log.info("SMS sent to {} for order {}", phoneNumber, orderId);
    return entry;
  }

  /** Emails the customer that the order was placed. */
  public NotificationLog notifyOrderPlaced(OrderPlacedEvent event) {
    if (event == null || isBlank(event.getOrderId())) {
      throw CodedErrorFactory.create(
          MappedError.NOTIF_4003, "orderId", NotificationTemplate.ORDER_PLACED_TEMPLATE);
    }
    if (isBlank(event.getUserId())) {
      throw CodedErrorFactory.create(MappedError.NOTIF_4001, NotificationLog.TYPE_EMAIL);
    }
    Map<String, Object> data = new HashMap<>();
    data.put("userId", event.getUserId());
    data.put("orderId", event.getOrderId());
    data.put("totalAmount", event.getTotalAmount());
    return sendEmail(emailFor(event.getUserId()), NotificationTemplate.ORDER_PLACED_TEMPLATE, data);
  }

  /**
   * Emails the payment outcome.
   *
   * <p>Payment events carry no user ID, so the user is resolved from earlier notifications of the
   * same order. When none exists the log is keyed by an order specific address.
   */
  public NotificationLog notifyPaymentResult(PaymentResultEvent event) {
    String templateId =
        event != null && event.isSuccess()
            ? NotificationTemplate.PAYMENT_SUCCESS_TEMPLATE
            : NotificationTemplate.PAYMENT_FAILED_TEMPLATE;
    if (event == null || isBlank(event.getOrderId())) {
      throw CodedErrorFactory.create(MappedError.NOTIF_4003, "orderId", templateId);
    }
    String userId = resolveUserId(event.getOrderId());
    Map<String, Object> data = new HashMap<>();
    data.put("orderId", event.getOrderId());
    data.put("transactionId", event.getTransactionId() == null ? "n/a" : event.getTransactionId());
    data.put("failureReason", event.getFailureReason() == null ? "unknown reason" : event.getFailureReason());
    if (userId != null) {
      data.put("userId", userId);
    }
    String recipient =
        userId != null ? emailFor(userId) : "order-" + event.getOrderId() + CUSTOMER_EMAIL_DOMAIN;
    return sendEmail(recipient, templateId, data);
  }

  public List<NotificationLog> getNotificationsByUserId(String userId) {
    return logRepository.findByUserId(userId);
  }

  public List<NotificationLog> getNotificationsByOrderId(String orderId) {
    return logRepository.findByOrderId(orderId);
  }

  /** Fills every placeholder, throwing NOTIF_4003 for the first one without a value. */
  String render(NotificationTemplate template, Map<String, Object> data) {
    Matcher matcher = PLACEHOLDER.matcher(template.getBodyTemplate());
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      Object value = data.get(matcher.group(1));
      if (value == null) {
        throw CodedErrorFactory.create(
            MappedError.NOTIF_4003, matcher.group(1), template.getTemplateId());
      }
      matcher.appendReplacement(out, Matcher.quoteReplacement(String.valueOf(value)));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  private String resolveUserId(String orderId) {
    return logRepository.findByOrderId(orderId).stream()
        .filter(entry -> NotificationTemplate.ORDER_PLACED_TEMPLATE.equals(entry.getTemplateId()))
        .map(NotificationLog::getUserId)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private void persist(NotificationLog entry) {
    try {
      logRepository.save(entry);
    } catch (RuntimeException ex) {
      throw CodedErrorFactory.create(MappedError.NOTIF_5003, ex, entry.getUserId());
    }
  }

  private static NotificationLog newLog(
      String userId, String orderId, String type, String templateId, String message) {
    return new NotificationLog(
        "NTF-" + UUID.randomUUID(),
        userId,
        orderId,
        type,
        templateId,
        message,
        Instant.now(),
        NotificationLog.STATUS_SENT);
  }

  static String emailFor(String userId) {
    return userId.contains("@") ? userId : userId + CUSTOMER_EMAIL_DOMAIN;
  }

  private static String stringOr(Object value, String fallback) {
    return value == null ? fallback : String.valueOf(value);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
