package com.ecommerce.product.repository;

import com.ecommerce.product.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link Product} rows. */
public interface ProductRepository extends JpaRepository<Product, String> {}
