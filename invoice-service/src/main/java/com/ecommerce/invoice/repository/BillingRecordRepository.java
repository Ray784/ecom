package com.ecommerce.invoice.repository;

import com.ecommerce.invoice.model.BillingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Persistence for billing breakdowns keyed by order ID. */
@Repository
public interface BillingRecordRepository extends JpaRepository<BillingRecord, String> {}
