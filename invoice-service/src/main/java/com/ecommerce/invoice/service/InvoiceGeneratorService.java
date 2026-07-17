package com.ecommerce.invoice.service;

import com.ecommerce.common.dto.InvoiceDTO;
import com.ecommerce.common.dto.OrderDetailsDTO;
import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.invoice.client.OrderServiceClient;
import com.ecommerce.invoice.model.BillingRecord;
import com.ecommerce.invoice.model.Invoice;
import com.ecommerce.invoice.repository.BillingRecordRepository;
import com.ecommerce.invoice.repository.InvoiceRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Calculates billing amounts, renders the PDF invoice and stores it on the local file system.
 *
 * <p>Pricing rules: orders with a subtotal of at least 100.00 get a 5 percent discount, and an 8
 * percent tax is applied to the discounted amount. All amounts are rounded half up to cents.
 * Generation is idempotent per order: asking again returns the invoice that already exists.
 */
@Service
public class InvoiceGeneratorService {

  static final BigDecimal TAX_RATE = new BigDecimal("0.08");
  static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.05");
  static final BigDecimal DISCOUNT_THRESHOLD = new BigDecimal("100.00");

  private static final Logger log = LoggerFactory.getLogger(InvoiceGeneratorService.class);
  private static final Pattern ORDER_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");
  private static final Set<String> BILLABLE_STATUSES = Set.of("PAID", "COMPLETED", "SHIPPED", "DELIVERED");

  private final InvoiceRepository invoiceRepository;
  private final BillingRecordRepository billingRecordRepository;
  private final OrderServiceClient orderServiceClient;
  private final InvoicePdfRenderer pdfRenderer;
  private final Path storageDir;
  private final BigDecimal fallbackSubtotal;

  public InvoiceGeneratorService(
      InvoiceRepository invoiceRepository,
      BillingRecordRepository billingRecordRepository,
      OrderServiceClient orderServiceClient,
      InvoicePdfRenderer pdfRenderer,
      @Value("${invoice.storage-dir:/tmp/invoices}") String storageDir,
      @Value("${invoice.fallback-subtotal:0.00}") BigDecimal fallbackSubtotal) {
    this.invoiceRepository = invoiceRepository;
    this.billingRecordRepository = billingRecordRepository;
    this.orderServiceClient = orderServiceClient;
    this.pdfRenderer = pdfRenderer;
    this.storageDir = Path.of(storageDir);
    this.fallbackSubtotal = fallbackSubtotal;
  }

  /** Generates the invoice for the order using the given subtotal, or returns the existing one. */
  public InvoiceDTO generateInvoice(String orderId, BigDecimal subtotal) {
    validateOrderId(orderId);
    Optional<Invoice> existing = invoiceRepository.findByOrderId(orderId);
    if (existing.isPresent()) {
      return toDto(existing.get());
    }
    if (subtotal == null) {
      throw CodedErrorFactory.create(MappedError.INV_4002, orderId);
    }
    BillingRecord billing;
    try {
      billing = calculateTaxAndDiscounts(subtotal);
    } catch (IllegalArgumentException | ArithmeticException ex) {
      throw CodedErrorFactory.create(MappedError.INV_5003, ex, orderId);
    }
    billing.setOrderId(orderId);
    billingRecordRepository.save(billing);

    Invoice invoice = new Invoice("INV-" + UUID.randomUUID(), orderId, billing.getTaxAmount(), Instant.now());
    byte[] pdf;
    try {
      pdf = pdfRenderer.render(invoice, billing);
    } catch (RuntimeException ex) {
      throw CodedErrorFactory.create(MappedError.INV_5001, ex, orderId);
    }
    try {
      return storeInvoiceDocument(invoice, pdf);
    } catch (DataIntegrityViolationException ex) {
      log.info("Invoice for order {} was generated concurrently, returning stored copy", orderId);
      return getInvoice(orderId);
    }
  }

  /** Generates the invoice from order details, which must be in a paid status. */
  public InvoiceDTO generateInvoiceForOrder(OrderDetailsDTO order) {
    if (order == null || order.getOrderId() == null) {
      throw CodedErrorFactory.create(MappedError.INV_4002, order == null ? null : order.getOrderId());
    }
    String status = order.getStatus() == null ? "" : order.getStatus().toUpperCase(Locale.ROOT);
    if (!BILLABLE_STATUSES.contains(status)) {
      throw CodedErrorFactory.create(MappedError.INV_4003, order.getOrderId());
    }
    return generateInvoice(order.getOrderId(), order.getTotalAmount());
  }

  /**
   * Generates the invoice for a successful payment.
   *
   * <p>The order total is read from the order service. When the order service is unreachable or
   * returns no usable total the configured fallback subtotal is billed, so a confirmed payment
   * always yields an invoice.
   */
  public InvoiceDTO generateInvoiceForPayment(PaymentResultEvent event) {
    if (event == null || event.getOrderId() == null || event.getOrderId().isBlank()) {
      throw CodedErrorFactory.create(MappedError.INV_4002, event == null ? null : event.getOrderId());
    }
    String orderId = event.getOrderId();
    if (!event.isSuccess()) {
      throw CodedErrorFactory.create(MappedError.INV_4003, orderId);
    }
    validateOrderId(orderId);
    if (invoiceRepository.findByOrderId(orderId).isPresent()) {
      return getInvoice(orderId);
    }
    BigDecimal subtotal =
        orderServiceClient
            .fetchOrder(orderId)
            .map(OrderDetailsDTO::getTotalAmount)
            .filter(total -> total.signum() >= 0)
            .orElseGet(
                () -> {
                  log.warn("Using fallback subtotal {} for order {}", fallbackSubtotal, orderId);
                  return fallbackSubtotal;
                });
    return generateInvoice(orderId, subtotal);
  }

  /** Looks the order up in the order service and generates its invoice. */
  public InvoiceDTO generateInvoiceForOrderId(String orderId) {
    validateOrderId(orderId);
    if (invoiceRepository.findByOrderId(orderId).isPresent()) {
      return getInvoice(orderId);
    }
    OrderDetailsDTO order =
        orderServiceClient
            .fetchOrder(orderId)
            .orElseThrow(() -> CodedErrorFactory.create(MappedError.INV_4002, orderId));
    return generateInvoiceForOrder(order);
  }

  /**
   * Computes discount, tax and final total for the subtotal.
   *
   * @return a billing record without order ID
   * @throws IllegalArgumentException when the subtotal is missing or negative
   */
  public BillingRecord calculateTaxAndDiscounts(BigDecimal subtotal) {
    if (subtotal == null || subtotal.signum() < 0) {
      throw new IllegalArgumentException("Subtotal must be a non negative amount: " + subtotal);
    }
    BigDecimal normalized = subtotal.setScale(2, RoundingMode.HALF_UP);
    BigDecimal discount =
        normalized.compareTo(DISCOUNT_THRESHOLD) >= 0
            ? normalized.multiply(DISCOUNT_RATE).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(2);
    BigDecimal taxable = normalized.subtract(discount);
    BigDecimal tax = taxable.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
    return new BillingRecord(null, normalized, discount, tax, taxable.add(tax));
  }

  /**
   * Writes the PDF to the storage directory as invoice-ORDERID.pdf, records its location and
   * persists the invoice.
   */
  public InvoiceDTO storeInvoiceDocument(Invoice invoice, byte[] pdfContent) {
    if (pdfContent == null || pdfContent.length == 0) {
      throw CodedErrorFactory.create(MappedError.INV_5001, invoice.getOrderId());
    }
    Path target = storageDir.resolve("invoice-" + invoice.getOrderId() + ".pdf");
    try {
      Files.createDirectories(storageDir);
      Files.write(target, pdfContent);
    } catch (IOException ex) {
      throw CodedErrorFactory.create(MappedError.INV_5002, ex);
    }
    invoice.setPdfStorageLink(target.toAbsolutePath().toString());
    invoiceRepository.save(invoice);
    log.info("Stored invoice {} for order {} at {}", invoice.getId(), invoice.getOrderId(), target);
    return toDto(invoice);
  }

  /** Returns the invoice of the order or throws INV_4001. */
  public InvoiceDTO getInvoice(String orderId) {
    return invoiceRepository
        .findByOrderId(orderId)
        .map(this::toDto)
        .orElseThrow(() -> CodedErrorFactory.create(MappedError.INV_4001, orderId));
  }

  private InvoiceDTO toDto(Invoice invoice) {
    BillingRecord billing = billingRecordRepository.findById(invoice.getOrderId()).orElse(null);
    InvoiceDTO dto =
        new InvoiceDTO(
            invoice.getId(),
            invoice.getOrderId(),
            invoice.getPdfStorageLink(),
            billing == null ? null : billing.getSubtotal(),
            billing == null ? null : billing.getDiscount(),
            invoice.getTaxAmount(),
            billing == null ? null : billing.getFinalTotal(),
            invoice.getIssueDate() == null ? null : invoice.getIssueDate().toString());
    dto.setSuccess(true);
    dto.setStatusCode(200);
    dto.setMessage("Invoice available for order " + invoice.getOrderId());
    return dto;
  }

  /** Rejects IDs that are blank or could escape the storage directory once used in a file name. */
  private static void validateOrderId(String orderId) {
    if (orderId == null || !ORDER_ID_PATTERN.matcher(orderId).matches()) {
      throw CodedErrorFactory.create(MappedError.INV_4002, orderId);
    }
  }
}
