package com.ecommerce.search.service;

import com.ecommerce.common.dto.ProductDetailsDTO;
import com.ecommerce.common.dto.ProductSummaryDTO;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.search.model.ProductSearchIndex;
import com.ecommerce.search.repository.ProductIndexStore;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Converts product payloads into search documents and upserts them into the index. */
@Service
public class ProductDataIndexer {

  private final ProductIndexStore store;

  public ProductDataIndexer(ProductIndexStore store) {
    this.store = store;
  }

  /** Indexes a product summary, replacing any document with the same SKU. */
  public ProductSearchIndex updateIndex(ProductSummaryDTO product) {
    if (product == null || product.getSku() == null || product.getSku().isBlank()) {
      throw CodedErrorFactory.create(
          MappedError.SEARCH_5002, product == null ? null : product.getSku());
    }
    String sku = product.getSku().trim();
    ProductSearchIndex document =
        new ProductSearchIndex(
            sku,
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getCategoryId() == null
                ? null
                : product.getCategoryId().trim().toUpperCase(Locale.ROOT),
            product.getImageUrl(),
            keywords(product.getName(), product.getDescription(), product.getCategoryId()));
    try {
      return store.save(document);
    } catch (RuntimeException e) {
      throw CodedErrorFactory.create(MappedError.SEARCH_5002, e, sku);
    }
  }

  /**
   * Indexes a full product details payload.
   *
   * <p>Details carry no image URL, so the image of an already indexed document is kept.
   */
  public ProductSearchIndex updateIndex(ProductDetailsDTO product) {
    if (product == null) {
      throw CodedErrorFactory.create(MappedError.SEARCH_5002, (Object) null);
    }
    String imageUrl =
        product.getSku() == null
            ? null
            : store.findById(product.getSku().trim()).map(ProductSearchIndex::getImageUrl).orElse(null);
    return updateIndex(
        new ProductSummaryDTO(
            product.getSku(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getCategoryId(),
            imageUrl));
  }

  /** Converts a stored document back into the public summary contract. */
  public static ProductSummaryDTO toSummary(ProductSearchIndex document) {
    return new ProductSummaryDTO(
        document.getProductId(),
        document.getName(),
        document.getDescription(),
        document.getPrice(),
        document.getCategoryId(),
        document.getImageUrl());
  }

  /** Lower cased, de-duplicated alphanumeric tokens of length two or more. */
  static List<String> keywords(String... sources) {
    Set<String> tokens = new LinkedHashSet<>();
    for (String source : sources) {
      if (source == null) {
        continue;
      }
      Arrays.stream(source.toLowerCase(Locale.ROOT).split("[^\\p{Alnum}]+"))
          .filter(token -> token.length() >= 2)
          .forEach(tokens::add);
    }
    return List.copyOf(tokens);
  }
}
