package com.ecommerce.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.product.model.Category;
import com.ecommerce.product.repository.CategoryRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CategoryServiceTest {

  private CategoryRepository repository;
  private CategoryService service;

  @BeforeEach
  void setUp() {
    repository = mock(CategoryRepository.class);
    service = new CategoryService(repository);
    when(repository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));
  }

  private static void assertCode(Runnable call, String code) {
    assertThatThrownBy(call::run)
        .isInstanceOf(ApplicationBaseException.class)
        .extracting("errorCode")
        .isEqualTo(code);
  }

  @Test
  void createCategory_normalizesIdAndTrimsName() {
    Category saved = service.createCategory(new Category(" toys ", " Toys and Games "));
    assertThat(saved.getId()).isEqualTo("TOYS");
    assertThat(saved.getName()).isEqualTo("Toys and Games");
  }

  @Test
  void createCategory_blankName_defaultsToId() {
    assertThat(service.createCategory(new Category("toys", null)).getName()).isEqualTo("TOYS");
  }

  @Test
  void createCategory_missingId_throwsProd4003() {
    assertCode(() -> service.createCategory(null), "PROD_4003");
    assertCode(() -> service.createCategory(new Category(" ", "x")), "PROD_4003");
  }

  @Test
  void getCategory_existing_returnsIt() {
    when(repository.findById("BOOKS")).thenReturn(Optional.of(new Category("BOOKS", "Books")));
    assertThat(service.getCategory("books").getName()).isEqualTo("Books");
  }

  @Test
  void getCategory_missingOrBlank_throwsProd4009() {
    when(repository.findById("NOPE")).thenReturn(Optional.empty());
    assertCode(() -> service.getCategory("NOPE"), "PROD_4009");
    assertCode(() -> service.getCategory(null), "PROD_4009");
  }

  @Test
  void exists_checksNormalizedIdAndRejectsBlank() {
    when(repository.existsById("BOOKS")).thenReturn(true);
    assertThat(service.exists("books")).isTrue();
    assertThat(service.exists("toys")).isFalse();
    assertThat(service.exists(null)).isFalse();
  }

  @Test
  void listCategories_delegatesToRepository() {
    when(repository.findAll()).thenReturn(List.of(new Category("A", "a")));
    assertThat(service.listCategories()).hasSize(1);
  }
}
