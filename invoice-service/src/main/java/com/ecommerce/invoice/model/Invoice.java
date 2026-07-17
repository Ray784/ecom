package com.ecommerce.invoice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Metadata of a generated invoice and the location of its PDF document. */
@Entity
@Table(name = "invoices")
public class Invoice {

  @Id
  @Column(length = 64)
  private String id;

  @Column(nullable = false, unique = true, length = 64)
  private String orderId;

  @Column(length = 512)
  private String pdfStorageLink;

  @Column(precision = 19, scale = 2)
  private BigDecimal taxAmount;

  @Column(nullable = false)
  private Instant issueDate;

  public Invoice() {}

  public Invoice(String id, String orderId, BigDecimal taxAmount, Instant issueDate) {
    this.id = id;
    this.orderId = orderId;
    this.taxAmount = taxAmount;
    this.issueDate = issueDate;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String orderId) {
    this.orderId = orderId;
  }

  public String getPdfStorageLink() {
    return pdfStorageLink;
  }

  public void setPdfStorageLink(String pdfStorageLink) {
    this.pdfStorageLink = pdfStorageLink;
  }

  public BigDecimal getTaxAmount() {
    return taxAmount;
  }

  public void setTaxAmount(BigDecimal taxAmount) {
    this.taxAmount = taxAmount;
  }

  public Instant getIssueDate() {
    return issueDate;
  }

  public void setIssueDate(Instant issueDate) {
    this.issueDate = issueDate;
  }
}
