package com.ecommerce.product.config;

import com.ecommerce.product.model.Category;
import com.ecommerce.product.repository.CategoryRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Inserts the default product categories on startup when they are missing. */
@Component
public class CategorySeeder implements ApplicationRunner {

  /** Categories that every fresh database starts with. */
  public static final List<Category> DEFAULT_CATEGORIES =
      List.of(
          new Category("ELECTRONICS", "Electronics"),
          new Category("BOOKS", "Books"),
          new Category("CLOTHING", "Clothing"),
          new Category("HOME", "Home and Kitchen"),
          new Category("SPORTS", "Sports and Outdoors"));

  private static final Logger log = LoggerFactory.getLogger(CategorySeeder.class);

  private final CategoryRepository categoryRepository;

  public CategorySeeder(CategoryRepository categoryRepository) {
    this.categoryRepository = categoryRepository;
  }

  @Override
  public void run(ApplicationArguments args) {
    int inserted = 0;
    for (Category category : DEFAULT_CATEGORIES) {
      if (!categoryRepository.existsById(category.getId())) {
        categoryRepository.save(new Category(category.getId(), category.getName()));
        inserted++;
      }
    }
    log.info("Category seeding complete, inserted {} default categories", inserted);
  }
}
