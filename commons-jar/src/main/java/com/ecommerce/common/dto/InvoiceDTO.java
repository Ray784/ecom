package com.ecommerce.common.dto;

import java.math.BigDecimal;
import java.util.Objects;

/** Billing document metadata and totals for a paid order. */
public class InvoiceDTO extends BaseResponseDTO {

  private String id;
  private String orderId;
  private String pdfStorageLink;
  private BigDecimal subtotal;
  private BigDecimal discount;
  private BigDecimal taxAmount;
  private BigDecimal finalTotal;
  private String issueDate;

  /** Creates an empty instance for JSON deserialization. */
  public InvoiceDTO() {}

  /** Creates a successful response with status 200 carrying the given payload fields. */
  public InvoiceDTO(String id, String orderId, String pdfStorageLink, BigDecimal subtotal, BigDecimal discount, BigDecimal taxAmount, BigDecimal finalTotal, String issueDate) {
    super(true, 200, "Success");
    this.id = id;
    this.orderId = orderId;
    this.pdfStorageLink = pdfStorageLink;
    this.subtotal = subtotal;
    this.discount = discount;
    this.taxAmount = taxAmount;
    this.finalTotal = finalTotal;
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

  public BigDecimal getSubtotal() {
    return subtotal;
  }

  public void setSubtotal(BigDecimal subtotal) {
    this.subtotal = subtotal;
  }

  public BigDecimal getDiscount() {
    return discount;
  }

  public void setDiscount(BigDecimal discount) {
    this.discount = discount;
  }

  public BigDecimal getTaxAmount() {
    return taxAmount;
  }

  public void setTaxAmount(BigDecimal taxAmount) {
    this.taxAmount = taxAmount;
  }

  public BigDecimal getFinalTotal() {
    return finalTotal;
  }

  public void setFinalTotal(BigDecimal finalTotal) {
    this.finalTotal = finalTotal;
  }

  public String getIssueDate() {
    return issueDate;
  }

  public void setIssueDate(String issueDate) {
    this.issueDate = issueDate;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!super.equals(o)) {
      return false;
    }
    InvoiceDTO that = (InvoiceDTO) o;
    return Objects.equals(id, that.id)
        && Objects.equals(orderId, that.orderId)
        && Objects.equals(pdfStorageLink, that.pdfStorageLink)
        && Objects.equals(subtotal, that.subtotal)
        && Objects.equals(discount, that.discount)
        && Objects.equals(taxAmount, that.taxAmount)
        && Objects.equals(finalTotal, that.finalTotal)
        && Objects.equals(issueDate, that.issueDate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), id, orderId, pdfStorageLink, subtotal, discount, taxAmount, finalTotal, issueDate);
  }

  @Override
  public String toString() {
    return "InvoiceDTO{" + "id=" + id
        + ", orderId=" + orderId
        + ", pdfStorageLink=" + pdfStorageLink
        + ", subtotal=" + subtotal
        + ", discount=" + discount
        + ", taxAmount=" + taxAmount
        + ", finalTotal=" + finalTotal
        + ", issueDate=" + issueDate
        + ", " + super.toString() + "}";
  }
}
