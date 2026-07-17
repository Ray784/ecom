package com.ecommerce.invoice.service;

import com.ecommerce.invoice.model.BillingRecord;
import com.ecommerce.invoice.model.Invoice;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Renders a single page PDF invoice without third party libraries.
 *
 * <p>The output is a minimal but valid PDF 1.4 file: catalog, page tree, one page, the built in
 * Helvetica font and one content stream, followed by a cross reference table with exact offsets.
 */
@Component
public class InvoicePdfRenderer {

  /** Builds the PDF bytes for the invoice and its billing breakdown. */
  public byte[] render(Invoice invoice, BillingRecord billing) {
    List<String> lines = new ArrayList<>();
    lines.add("INVOICE " + invoice.getId());
    lines.add("Order: " + invoice.getOrderId());
    lines.add("Issue date: " + invoice.getIssueDate());
    lines.add("Subtotal: " + billing.getSubtotal());
    lines.add("Discount: " + billing.getDiscount());
    lines.add("Tax: " + billing.getTaxAmount());
    lines.add("Total: " + billing.getFinalTotal());

    StringBuilder content = new StringBuilder("BT /F1 12 Tf 72 760 Td 16 TL\n");
    for (String line : lines) {
      content.append('(').append(escape(line)).append(") Tj T*\n");
    }
    content.append("ET\n");
    byte[] stream = content.toString().getBytes(StandardCharsets.US_ASCII);

    List<String> objects =
        List.of(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                + "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            "<< /Length " + stream.length + " >>\nstream\n"
                + new String(stream, StandardCharsets.US_ASCII) + "endstream");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    write(out, "%PDF-1.4\n");
    List<Integer> offsets = new ArrayList<>();
    for (int i = 0; i < objects.size(); i++) {
      offsets.add(out.size());
      write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
    }
    int xrefOffset = out.size();
    StringBuilder xref = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n");
    xref.append("0000000000 65535 f \n");
    for (int offset : offsets) {
      xref.append(String.format("%010d 00000 n \n", offset));
    }
    xref.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n");
    xref.append("startxref\n").append(xrefOffset).append("\n%%EOF\n");
    write(out, xref.toString());
    return out.toByteArray();
  }

  /** Escapes characters that are special inside PDF literal strings and drops non ASCII. */
  static String escape(String text) {
    StringBuilder sb = new StringBuilder(text.length());
    for (char c : text.toCharArray()) {
      if (c == '\\' || c == '(' || c == ')') {
        sb.append('\\').append(c);
      } else if (c >= 32 && c < 127) {
        sb.append(c);
      } else {
        sb.append('?');
      }
    }
    return sb.toString();
  }

  private static void write(ByteArrayOutputStream out, String text) {
    out.writeBytes(text.getBytes(StandardCharsets.US_ASCII));
  }
}
