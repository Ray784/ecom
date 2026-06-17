package com.ecommerce.order.repository;

import com.ecommerce.order.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for orders keyed by order ID. */
public interface OrderRepository extends JpaRepository<Order, String> {}
