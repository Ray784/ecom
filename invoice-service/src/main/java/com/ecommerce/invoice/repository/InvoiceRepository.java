package com.ecommerce.invoice.repository;

import com.ecommerce.invoice.model.Invoice;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Persistence for invoice metadata. */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, String> {

  Optional<Invoice> findByOrderId(String orderId);
}
