package com.ecommerce.invoice.controller;

import com.ecommerce.common.dto.InvoiceDTO;
import com.ecommerce.invoice.service.InvoiceGeneratorService;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Invoice retrieval and internal generation endpoints. */
@RestController
@RequestMapping("/invoices")
public class InvoiceController {

  private final InvoiceGeneratorService invoiceGeneratorService;

  public InvoiceController(InvoiceGeneratorService invoiceGeneratorService) {
    this.invoiceGeneratorService = invoiceGeneratorService;
  }

  /** Returns the invoice of the order, INV_4001 when it has not been generated yet. */
  @GetMapping("/{orderId}")
  public InvoiceDTO getInvoice(@PathVariable String orderId) {
    return invoiceGeneratorService.getInvoice(orderId);
  }

  /**
   * Internal or test trigger.
   *
   * <p>With an amount the invoice is billed for that subtotal directly. Without it the order is
   * read from the order service and must be in a paid status.
   */
  @PostMapping("/generate/{orderId}")
  @ResponseStatus(HttpStatus.CREATED)
  public InvoiceDTO generate(
      @PathVariable String orderId, @RequestParam(required = false) BigDecimal amount) {
    return amount != null
        ? invoiceGeneratorService.generateInvoice(orderId, amount)
        : invoiceGeneratorService.generateInvoiceForOrderId(orderId);
  }
}
