package com.ecommerce.product.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.product.model.Category;
import com.ecommerce.product.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class CategorySeederTest {

  @Test
  void run_emptyDatabase_insertsAllDefaults() {
    CategoryRepository repository = mock(CategoryRepository.class);
    when(repository.existsById(anyString())).thenReturn(false);

    new CategorySeeder(repository).run(new DefaultApplicationArguments());

    verify(repository, times(CategorySeeder.DEFAULT_CATEGORIES.size())).save(any(Category.class));
  }

  @Test
  void run_categoriesPresent_insertsNothing() {
    CategoryRepository repository = mock(CategoryRepository.class);
    when(repository.existsById(anyString())).thenReturn(true);

    new CategorySeeder(repository).run(new DefaultApplicationArguments());

    verify(repository, never()).save(any(Category.class));
  }
}
