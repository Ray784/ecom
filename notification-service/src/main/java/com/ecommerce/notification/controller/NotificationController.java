package com.ecommerce.notification.controller;

import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.notification.dto.SendNotificationRequest;
import com.ecommerce.notification.model.NotificationLog;
import com.ecommerce.notification.service.NotificationSenderService;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Notification history lookups and the internal send endpoint. */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

  private final NotificationSenderService senderService;

  public NotificationController(NotificationSenderService senderService) {
    this.senderService = senderService;
  }

  @GetMapping("/user/{userId}")
  public List<NotificationLog> byUser(@PathVariable String userId) {
    return senderService.getNotificationsByUserId(userId);
  }

  @GetMapping("/order/{orderId}")
  public List<NotificationLog> byOrder(@PathVariable String orderId) {
    return senderService.getNotificationsByOrderId(orderId);
  }

  /**
   * Internal or test endpoint that sends one notification.
   *
   * <p>An unsupported type is reported as NOTIF_4001 because no recipient address can be valid for
   * a channel that does not exist.
   */
  @PostMapping("/send")
  @ResponseStatus(HttpStatus.CREATED)
  public NotificationLog send(@RequestBody SendNotificationRequest request) {
    String type =
        request.type() == null ? NotificationLog.TYPE_EMAIL : request.type().toUpperCase(Locale.ROOT);
    return switch (type) {
      case NotificationLog.TYPE_EMAIL -> {
        Map<String, Object> data = request.data() == null ? new HashMap<>() : new HashMap<>(request.data());
        if (request.userId() != null) {
          data.putIfAbsent("userId", request.userId());
        }
        if (request.orderId() != null) {
          data.putIfAbsent("orderId", request.orderId());
        }
        yield senderService.sendEmail(request.recipient(), request.templateId(), data);
      }
      case NotificationLog.TYPE_SMS ->
          senderService.sendSms(
              request.recipient(), request.message(), request.userId(), request.orderId());
      default -> throw CodedErrorFactory.create(MappedError.NOTIF_4001, request.type());
    };
  }
}
