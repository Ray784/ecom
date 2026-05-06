package com.ecommerce.product.repository;

import com.ecommerce.product.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link Category} rows. */
public interface CategoryRepository extends JpaRepository<Category, String> {}
