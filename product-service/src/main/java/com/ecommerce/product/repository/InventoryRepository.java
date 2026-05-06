package com.ecommerce.product.repository;

import com.ecommerce.product.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link Inventory} rows. */
public interface InventoryRepository extends JpaRepository<Inventory, String> {}
