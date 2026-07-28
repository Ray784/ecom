package com.ecommerce.notification.model;

import java.time.Instant;

/** History entry for one notification attempt, stored as a document in the in memory store. */
public class NotificationLog {

  public static final String TYPE_EMAIL = "EMAIL";
  public static final String TYPE_SMS = "SMS";
  public static final String STATUS_SENT = "SENT";
  public static final String STATUS_FAILED = "FAILED";

  private String id;
  private String userId;
  private String orderId;
  private String type;
  private String templateId;
  private String message;
  private Instant sentDate;
  private String status;

  public NotificationLog() {}

  public NotificationLog(
      String id,
      String userId,
      String orderId,
      String type,
      String templateId,
      String message,
      Instant sentDate,
      String status) {
    this.id = id;
    this.userId = userId;
    this.orderId = orderId;
    this.type = type;
    this.templateId = templateId;
    this.message = message;
    this.sentDate = sentDate;
    this.status = status;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String orderId) {
    this.orderId = orderId;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getTemplateId() {
    return templateId;
  }

  public void setTemplateId(String templateId) {
    this.templateId = templateId;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Instant getSentDate() {
    return sentDate;
  }

  public void setSentDate(Instant sentDate) {
    this.sentDate = sentDate;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  @Override
  public String toString() {
    return "NotificationLog{id=" + id + ", userId=" + userId + ", orderId=" + orderId
        + ", type=" + type + ", templateId=" + templateId + ", status=" + status + "}";
  }
}
