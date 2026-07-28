package com.ecommerce.notification.dto;

import java.util.Map;

/**
 * Body of the internal send endpoint.
 *
 * @param type EMAIL or SMS, EMAIL when omitted
 * @param recipient email address or phone number
 * @param templateId template to render for EMAIL
 * @param data template values for EMAIL
 * @param message free text for SMS
 * @param userId optional user to link the log entry to
 * @param orderId optional order to link the log entry to
 */
public record SendNotificationRequest(
    String type,
    String recipient,
    String templateId,
    Map<String, Object> data,
    String message,
    String userId,
    String orderId) {}
