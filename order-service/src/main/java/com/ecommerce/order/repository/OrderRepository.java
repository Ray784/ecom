package com.ecommerce.order.repository;

import com.ecommerce.order.model.Order;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for orders keyed by order ID. */
public interface OrderRepository extends JpaRepository<Order, String> {

  /** Returns all orders of a user, newest first. */
  List<Order> findByUserIdOrderByCreationDateDesc(String userId);

  /** Returns true when the user has an order in the given status containing the given SKU. */
  boolean existsByUserIdAndStatusAndItemsSku(String userId, String status, String sku);
}
