package com.ecommerce.invoice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.common.dto.InvoiceDTO;
import com.ecommerce.common.dto.OrderDetailsDTO;
import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.invoice.client.OrderServiceClient;
import com.ecommerce.invoice.model.BillingRecord;
import com.ecommerce.invoice.model.Invoice;
import com.ecommerce.invoice.repository.BillingRecordRepository;
import com.ecommerce.invoice.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

class InvoiceGeneratorServiceTest {

  @TempDir Path tempDir;

  private final Map<String, Invoice> invoices = new HashMap<>();
  private final Map<String, BillingRecord> billing = new HashMap<>();
  private InvoiceRepository invoiceRepository;
  private BillingRecordRepository billingRepository;
  private OrderServiceClient orderClient;
  private InvoicePdfRenderer renderer;
  private InvoiceGeneratorService service;

  @BeforeEach
  void setUp() {
    invoiceRepository = mock(InvoiceRepository.class);
    when(invoiceRepository.save(any(Invoice.class)))
        .thenAnswer(inv -> {
          Invoice invoice = inv.getArgument(0);
          invoices.put(invoice.getOrderId(), invoice);
          return invoice;
        });
    when(invoiceRepository.findByOrderId(anyString()))
        .thenAnswer(inv -> Optional.ofNullable(invoices.get(inv.getArgument(0))));
    billingRepository = mock(BillingRecordRepository.class);
    when(billingRepository.save(any(BillingRecord.class)))
        .thenAnswer(inv -> {
          BillingRecord record = inv.getArgument(0);
          billing.put(record.getOrderId(), record);
          return record;
        });
    when(billingRepository.findById(anyString()))
        .thenAnswer(inv -> Optional.ofNullable(billing.get(inv.getArgument(0))));
    orderClient = mock(OrderServiceClient.class);
    when(orderClient.fetchOrder(anyString())).thenReturn(Optional.empty());
    renderer = spy(new InvoicePdfRenderer());
    service = newService(tempDir.resolve("invoices"));
  }

  private InvoiceGeneratorService newService(Path storage) {
    return new InvoiceGeneratorService(
        invoiceRepository, billingRepository, orderClient, renderer, storage.toString(),
        new BigDecimal("0.00"));
  }

  private static OrderDetailsDTO order(String orderId, String status, String total) {
    return new OrderDetailsDTO(orderId, "alice", status, new BigDecimal(total), "1 Main St",
        List.of(), Instant.now().toString());
  }

  @ParameterizedTest
  @CsvSource({
    "50.00, 50.00, 0.00, 4.00, 54.00",
    "99.99, 99.99, 0.00, 8.00, 107.99",
    "100.00, 100.00, 5.00, 7.60, 102.60",
    "19.999, 20.00, 0.00, 1.60, 21.60",
    "0, 0.00, 0.00, 0.00, 0.00"
  })
  void calculateTaxAndDiscounts_appliesThresholdDiscountAndTax(
      String input, String subtotal, String discount, String tax, String total) {
    BillingRecord record = service.calculateTaxAndDiscounts(new BigDecimal(input));

    assertThat(record.getSubtotal()).isEqualByComparingTo(subtotal);
    assertThat(record.getDiscount()).isEqualByComparingTo(discount);
    assertThat(record.getTaxAmount()).isEqualByComparingTo(tax);
    assertThat(record.getFinalTotal()).isEqualByComparingTo(total);
  }

  @Test
  void calculateTaxAndDiscounts_negativeOrNull_isRejected() {
    assertThatThrownBy(() -> service.calculateTaxAndDiscounts(new BigDecimal("-1")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.calculateTaxAndDiscounts(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void generateInvoice_writesPdfAndReturnsCompleteDto() throws Exception {
    InvoiceDTO dto = service.generateInvoice("ORD-1", new BigDecimal("200.00"));

    Path pdf = tempDir.resolve("invoices").resolve("invoice-ORD-1.pdf");
    assertThat(pdf).exists();
    assertThat(new String(Files.readAllBytes(pdf), StandardCharsets.US_ASCII)).startsWith("%PDF-1.4");
    assertThat(dto.getPdfStorageLink()).isEqualTo(pdf.toAbsolutePath().toString());
    assertThat(dto.getId()).startsWith("INV-");
    assertThat(dto.getOrderId()).isEqualTo("ORD-1");
    assertThat(dto.getSubtotal()).isEqualByComparingTo("200.00");
    assertThat(dto.getDiscount()).isEqualByComparingTo("10.00");
    assertThat(dto.getTaxAmount()).isEqualByComparingTo("15.20");
    assertThat(dto.getFinalTotal()).isEqualByComparingTo("205.20");
    assertThat(dto.getIssueDate()).isNotBlank();
    assertThat(dto.isSuccess()).isTrue();
    assertThat(billing.get("ORD-1").getFinalTotal()).isEqualByComparingTo("205.20");
  }

  @Test
  void generateInvoice_existingInvoice_isReturnedWithoutRegeneration() {
    InvoiceDTO first = service.generateInvoice("ORD-2", BigDecimal.TEN);

    InvoiceDTO second = service.generateInvoice("ORD-2", new BigDecimal("999"));

    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(second.getSubtotal()).isEqualByComparingTo("10.00");
    verify(renderer).render(any(), any());
  }

  @Test
  void generateInvoice_missingSubtotal_throwsInv4002() {
    assertThatThrownBy(() -> service.generateInvoice("ORD-3", null))
        .extracting("errorCode")
        .isEqualTo("INV_4002");
  }

  @ParameterizedTest
  @ValueSource(strings = {"../etc/passwd", "a/b", "", "order id"})
  void generateInvoice_unsafeOrderId_throwsInv4002(String orderId) {
    assertThatThrownBy(() -> service.generateInvoice(orderId, BigDecimal.TEN))
        .extracting("errorCode")
        .isEqualTo("INV_4002");
  }

  @Test
  void generateInvoice_negativeSubtotal_throwsInv5003() {
    assertThatThrownBy(() -> service.generateInvoice("ORD-4", new BigDecimal("-3")))
        .extracting("errorCode")
        .isEqualTo("INV_5003");
  }

  @Test
  void generateInvoice_rendererFails_throwsInv5001() {
    doThrow(new IllegalStateException("font")).when(renderer).render(any(), any());

    assertThatThrownBy(() -> service.generateInvoice("ORD-5", BigDecimal.TEN))
        .extracting("errorCode")
        .isEqualTo("INV_5001");
  }

  @Test
  void generateInvoice_concurrentDuplicate_returnsStoredInvoice() {
    Invoice stored = new Invoice("INV-other", "ORD-6", BigDecimal.ONE, Instant.now());
    when(invoiceRepository.save(any(Invoice.class)))
        .thenAnswer(inv -> {
          invoices.put("ORD-6", stored);
          throw new DataIntegrityViolationException("duplicate order id");
        });

    assertThat(service.generateInvoice("ORD-6", BigDecimal.TEN).getId()).isEqualTo("INV-other");
  }

  @Test
  void storeInvoiceDocument_emptyContent_throwsInv5001() {
    Invoice invoice = new Invoice("INV-1", "ORD-7", BigDecimal.ONE, Instant.now());

    assertThatThrownBy(() -> service.storeInvoiceDocument(invoice, new byte[0]))
        .extracting("errorCode")
        .isEqualTo("INV_5001");
  }

  @Test
  void storeInvoiceDocument_unwritableStorage_throwsInv5002() throws Exception {
    Path blocker = Files.writeString(tempDir.resolve("not-a-dir"), "x");
    InvoiceGeneratorService blocked = newService(blocker);
    Invoice invoice = new Invoice("INV-1", "ORD-8", BigDecimal.ONE, Instant.now());

    assertThatThrownBy(() -> blocked.storeInvoiceDocument(invoice, new byte[] {1}))
        .extracting("errorCode")
        .isEqualTo("INV_5002");
  }

  @Test
  void generateInvoiceForOrder_paidOrder_billsOrderTotal() {
    InvoiceDTO dto = service.generateInvoiceForOrder(order("ORD-9", "paid", "40.00"));

    assertThat(dto.getSubtotal()).isEqualByComparingTo("40.00");
  }

  @ParameterizedTest
  @ValueSource(strings = {"PENDING", "INVENTORY_RESERVED", "CANCELLED", "FAILED"})
  void generateInvoiceForOrder_unpaidOrder_throwsInv4003(String status) {
    assertThatThrownBy(() -> service.generateInvoiceForOrder(order("ORD-10", status, "40.00")))
        .extracting("errorCode")
        .isEqualTo("INV_4003");
  }

  @Test
  void generateInvoiceForOrder_nullStatus_throwsInv4003() {
    OrderDetailsDTO order = order("ORD-10", "PAID", "1.00");
    order.setStatus(null);

    assertThatThrownBy(() -> service.generateInvoiceForOrder(order))
        .extracting("errorCode")
        .isEqualTo("INV_4003");
  }

  @Test
  void generateInvoiceForOrder_nullOrder_throwsInv4002() {
    assertThatThrownBy(() -> service.generateInvoiceForOrder(null))
        .extracting("errorCode")
        .isEqualTo("INV_4002");
  }

  @Test
  void generateInvoiceForPayment_orderServiceAvailable_billsOrderTotal() {
    when(orderClient.fetchOrder("ORD-11")).thenReturn(Optional.of(order("ORD-11", "PENDING", "120.00")));

    InvoiceDTO dto = service.generateInvoiceForPayment(new PaymentResultEvent("ORD-11", "TXN", true, null));

    assertThat(dto.getSubtotal()).isEqualByComparingTo("120.00");
    assertThat(dto.getFinalTotal()).isEqualByComparingTo("123.12");
  }

  @Test
  void generateInvoiceForPayment_orderServiceDown_usesFallbackSubtotal() {
    InvoiceDTO dto = service.generateInvoiceForPayment(new PaymentResultEvent("ORD-12", "TXN", true, null));

    assertThat(dto.getSubtotal()).isEqualByComparingTo("0.00");
    assertThat(dto.getPdfStorageLink()).endsWith("invoice-ORD-12.pdf");
  }

  @Test
  void generateInvoiceForPayment_orderWithoutTotal_usesFallbackSubtotal() {
    OrderDetailsDTO noTotal = order("ORD-13", "PAID", "1.00");
    noTotal.setTotalAmount(null);
    when(orderClient.fetchOrder("ORD-13")).thenReturn(Optional.of(noTotal));

    assertThat(service.generateInvoiceForPayment(new PaymentResultEvent("ORD-13", "T", true, null))
            .getSubtotal())
        .isEqualByComparingTo("0.00");
  }

  @Test
  void generateInvoiceForPayment_existingInvoice_skipsOrderLookup() {
    service.generateInvoice("ORD-14", BigDecimal.ONE);

    service.generateInvoiceForPayment(new PaymentResultEvent("ORD-14", "T", true, null));

    verify(orderClient, never()).fetchOrder("ORD-14");
  }

  @Test
  void generateInvoiceForPayment_failedPayment_throwsInv4003() {
    assertThatThrownBy(() -> service.generateInvoiceForPayment(new PaymentResultEvent("ORD-15", "T", false, "x")))
        .extracting("errorCode")
        .isEqualTo("INV_4003");
  }

  @Test
  void generateInvoiceForPayment_missingOrderId_throwsInv4002() {
    assertThatThrownBy(() -> service.generateInvoiceForPayment(null))
        .extracting("errorCode")
        .isEqualTo("INV_4002");
    assertThatThrownBy(() -> service.generateInvoiceForPayment(new PaymentResultEvent(" ", "T", true, null)))
        .extracting("errorCode")
        .isEqualTo("INV_4002");
  }

  @Test
  void generateInvoiceForOrderId_unknownOrder_throwsInv4002() {
    assertThatThrownBy(() -> service.generateInvoiceForOrderId("ORD-16"))
        .extracting("errorCode")
        .isEqualTo("INV_4002");
  }

  @Test
  void generateInvoiceForOrderId_paidOrder_generatesAndIsIdempotent() {
    when(orderClient.fetchOrder("ORD-17")).thenReturn(Optional.of(order("ORD-17", "PAID", "60.00")));

    InvoiceDTO first = service.generateInvoiceForOrderId("ORD-17");
    InvoiceDTO second = service.generateInvoiceForOrderId("ORD-17");

    assertThat(second.getId()).isEqualTo(first.getId());
    verify(orderClient).fetchOrder("ORD-17");
  }

  @Test
  void getInvoice_missing_throwsInv4001() {
    assertThatThrownBy(() -> service.getInvoice("ORD-404"))
        .extracting("errorCode")
        .isEqualTo("INV_4001");
  }
}
