package com.ecommerce.invoice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.invoice.model.BillingRecord;
import com.ecommerce.invoice.model.Invoice;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class InvoicePdfRendererTest {

  private final InvoicePdfRenderer renderer = new InvoicePdfRenderer();

  private String renderSample() {
    Invoice invoice = new Invoice("INV-1", "ORD-1", new BigDecimal("1.60"), Instant.parse("2026-10-10T00:00:00Z"));
    BillingRecord billing =
        new BillingRecord("ORD-1", new BigDecimal("20.00"), new BigDecimal("0.00"),
            new BigDecimal("1.60"), new BigDecimal("21.60"));
    return new String(renderer.render(invoice, billing), StandardCharsets.US_ASCII);
  }

  @Test
  void render_producesPdfWithInvoiceFields() {
    String pdf = renderSample();

    assertThat(pdf).startsWith("%PDF-1.4\n").endsWith("%%EOF\n");
    assertThat(pdf).contains("INVOICE INV-1", "Order: ORD-1", "Total: 21.60", "/BaseFont /Helvetica");
  }

  @Test
  void render_crossReferenceOffsetsPointAtObjects() {
    String pdf = renderSample();

    Matcher startxref = Pattern.compile("startxref\n(\\d+)\n").matcher(pdf);
    assertThat(startxref.find()).isTrue();
    assertThat(pdf.substring(Integer.parseInt(startxref.group(1)))).startsWith("xref\n0 6\n");

    Matcher entries = Pattern.compile("(\\d{10}) 00000 n ").matcher(pdf);
    int objectNumber = 1;
    while (entries.find()) {
      int offset = Integer.parseInt(entries.group(1));
      assertThat(pdf.substring(offset)).startsWith(objectNumber + " 0 obj");
      objectNumber++;
    }
    assertThat(objectNumber).isEqualTo(6);
  }

  @Test
  void render_streamLengthMatchesContent() {
    String pdf = renderSample();

    Matcher length = Pattern.compile("/Length (\\d+) >>\nstream\n").matcher(pdf);
    assertThat(length.find()).isTrue();
    int start = length.end();
    assertThat(pdf.indexOf("endstream", start) - start).isEqualTo(Integer.parseInt(length.group(1)));
  }

  @Test
  void escape_quotesPdfDelimitersAndReplacesNonAscii() {
    assertThat(InvoicePdfRenderer.escape("a(b)c\\d\u00e9")).isEqualTo("a\\(b\\)c\\\\d?");
  }
}
