package com.ecommerce.notification.model;

/**
 * Predefined message body for one channel.
 *
 * <p>Placeholders use the form {name} and are filled from the data map passed by the sender.
 */
public class NotificationTemplate {

  public static final String ORDER_PLACED_TEMPLATE = "ORDER_PLACED_TEMPLATE";
  public static final String PAYMENT_SUCCESS_TEMPLATE = "PAYMENT_SUCCESS_TEMPLATE";
  public static final String PAYMENT_FAILED_TEMPLATE = "PAYMENT_FAILED_TEMPLATE";

  private String templateId;
  private String channel;
  private String bodyTemplate;

  public NotificationTemplate() {}

  public NotificationTemplate(String templateId, String channel, String bodyTemplate) {
    this.templateId = templateId;
    this.channel = channel;
    this.bodyTemplate = bodyTemplate;
  }

  public String getTemplateId() {
    return templateId;
  }

  public void setTemplateId(String templateId) {
    this.templateId = templateId;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public String getBodyTemplate() {
    return bodyTemplate;
  }

  public void setBodyTemplate(String bodyTemplate) {
    this.bodyTemplate = bodyTemplate;
  }
}
