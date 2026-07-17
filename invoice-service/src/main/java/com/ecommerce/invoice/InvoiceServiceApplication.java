package com.ecommerce.invoice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point of the invoice service that bills paid orders and stores PDF invoices. */
@SpringBootApplication
public class InvoiceServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(InvoiceServiceApplication.class, args);
  }
}
