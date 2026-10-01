package com.ecommerce.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ecommerce.common.dto.ProductDetailsDTO;
import com.ecommerce.common.dto.ProductSummaryDTO;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.search.model.ProductSearchIndex;
import com.ecommerce.search.repository.ProductIndexStore;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductDataIndexerTest {

  private ProductIndexStore store;
  private ProductDataIndexer indexer;

  @BeforeEach
  void setUp() {
    store = new ProductIndexStore();
    indexer = new ProductDataIndexer(store);
  }

  private static void assertCode(Runnable call, String code) {
    assertThatThrownBy(call::run)
        .isInstanceOf(ApplicationBaseException.class)
        .extracting("errorCode")
        .isEqualTo(code);
  }

  @Test
  void updateIndex_summary_storesDocumentWithKeywordsAndNormalizedCategory() {
    ProductSearchIndex doc =
        indexer.updateIndex(
            new ProductSummaryDTO(" P1 ", "Smart Phone", "5G, OLED a", BigDecimal.TEN, "electronics", "img"));

    assertThat(doc.getProductId()).isEqualTo("P1");
    assertThat(doc.getCategoryId()).isEqualTo("ELECTRONICS");
    assertThat(doc.getKeywords()).containsExactly("smart", "phone", "5g", "oled", "electronics");
    assertThat(store.findById("P1")).isPresent();
  }

  @Test
  void updateIndex_sameSku_replacesDocument() {
    indexer.updateIndex(new ProductSummaryDTO("P1", "Old", null, BigDecimal.ONE, null, null));
    indexer.updateIndex(new ProductSummaryDTO("P1", "New", null, BigDecimal.ONE, null, null));

    assertThat(store.count()).isEqualTo(1);
    assertThat(store.findById("P1").orElseThrow().getName()).isEqualTo("New");
    assertThat(store.findById("P1").orElseThrow().getCategoryId()).isNull();
  }

  @Test
  void updateIndex_missingSku_throwsSearch5002() {
    assertCode(() -> indexer.updateIndex((ProductSummaryDTO) null), "SEARCH_5002");
    assertCode(
        () -> indexer.updateIndex(new ProductSummaryDTO(" ", "n", null, null, null, null)),
        "SEARCH_5002");
    assertCode(() -> indexer.updateIndex((ProductDetailsDTO) null), "SEARCH_5002");
  }

  @Test
  void updateIndex_storeFailure_throwsSearch5002() {
    ProductIndexStore failing = mock(ProductIndexStore.class);
    when(failing.save(any())).thenThrow(new IllegalStateException("disk full"));

    assertCode(
        () ->
            new ProductDataIndexer(failing)
                .updateIndex(new ProductSummaryDTO("P1", "n", null, null, null, null)),
        "SEARCH_5002");
  }

  @Test
  void updateIndex_details_keepsExistingImageUrl() {
    indexer.updateIndex(new ProductSummaryDTO("P1", "Old", null, BigDecimal.ONE, "BOOKS", "img"));

    ProductSearchIndex doc =
        indexer.updateIndex(
            new ProductDetailsDTO("P1", "New", "d", BigDecimal.TEN, "BOOKS", 1, 0, null));

    assertThat(doc.getName()).isEqualTo("New");
    assertThat(doc.getImageUrl()).isEqualTo("img");
  }

  @Test
  void updateIndex_detailsForNewSku_hasNoImage() {
    ProductSearchIndex doc =
        indexer.updateIndex(new ProductDetailsDTO("P2", "n", null, BigDecimal.TEN, "BOOKS", 1, 0, null));
    assertThat(doc.getImageUrl()).isNull();
    assertCode(
        () -> indexer.updateIndex(new ProductDetailsDTO(null, "n", null, null, null, 0, 0, null)),
        "SEARCH_5002");
  }
}
