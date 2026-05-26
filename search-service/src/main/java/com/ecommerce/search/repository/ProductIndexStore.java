package com.ecommerce.search.repository;

import com.ecommerce.search.model.ProductSearchIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * Thread safe in-memory document store that acts as the embedded NoSQL search index.
 *
 * <p>Documents are keyed by product id and saving an existing id replaces the document.
 */
@Repository
public class ProductIndexStore {

  private final Map<String, ProductSearchIndex> documents = new ConcurrentHashMap<>();

  public ProductSearchIndex save(ProductSearchIndex document) {
    documents.put(document.getProductId(), document);
    return document;
  }

  public Optional<ProductSearchIndex> findById(String productId) {
    return Optional.ofNullable(documents.get(productId));
  }

  public List<ProductSearchIndex> findAll() {
    return new ArrayList<>(documents.values());
  }

  public long count() {
    return documents.size();
  }

  public void deleteAll() {
    documents.clear();
  }
}
