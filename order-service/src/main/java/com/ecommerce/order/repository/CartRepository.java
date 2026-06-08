package com.ecommerce.order.repository;

import com.ecommerce.order.model.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for carts keyed by user ID. */
public interface CartRepository extends JpaRepository<Cart, String> {}
