package com.ecommerce.product.service;

import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.product.model.Category;
import com.ecommerce.product.repository.CategoryRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** Creates and looks up product categories. */
@Service
public class CategoryService {

  private final CategoryRepository categoryRepository;

  public CategoryService(CategoryRepository categoryRepository) {
    this.categoryRepository = categoryRepository;
  }

  /**
   * Creates or renames a category. Ids are normalized to upper case so ELECTRONICS and electronics
   * refer to the same category.
   */
  public Category createCategory(Category request) {
    if (request == null || isBlank(request.getId())) {
      throw CodedErrorFactory.create(
          MappedError.PROD_4003, request == null ? null : request.getId());
    }
    String id = normalizeId(request.getId());
    String name = isBlank(request.getName()) ? id : request.getName().trim();
    return categoryRepository.save(new Category(id, name));
  }

  /** Returns the category or throws PROD_4009 when it does not exist. */
  public Category getCategory(String id) {
    if (isBlank(id)) {
      throw CodedErrorFactory.create(MappedError.PROD_4009, id);
    }
    return categoryRepository
        .findById(normalizeId(id))
        .orElseThrow(() -> CodedErrorFactory.create(MappedError.PROD_4009, id));
  }

  public List<Category> listCategories() {
    return categoryRepository.findAll();
  }

  /** Returns true when the id refers to an existing category. */
  public boolean exists(String id) {
    return !isBlank(id) && categoryRepository.existsById(normalizeId(id));
  }

  /** Upper cases and trims a category id. */
  public static String normalizeId(String id) {
    return id.trim().toUpperCase(Locale.ROOT);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
