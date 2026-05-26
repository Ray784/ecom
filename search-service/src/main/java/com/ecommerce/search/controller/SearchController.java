package com.ecommerce.search.controller;

import com.ecommerce.common.dto.BaseResponseDTO;
import com.ecommerce.common.dto.ProductSummaryDTO;
import com.ecommerce.common.dto.SearchResponseDTO;
import com.ecommerce.search.model.ProductSearchIndex;
import com.ecommerce.search.model.SearchQuery;
import com.ecommerce.search.service.ProductDataIndexer;
import com.ecommerce.search.service.SearchQueryService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public search endpoint plus the internal indexing hook used by product-service. */
@RestController
@RequestMapping("/search")
public class SearchController {

  private final SearchQueryService searchQueryService;
  private final ProductDataIndexer productDataIndexer;

  public SearchController(
      SearchQueryService searchQueryService, ProductDataIndexer productDataIndexer) {
    this.searchQueryService = searchQueryService;
    this.productDataIndexer = productDataIndexer;
  }

  /**
   * Searches products. q and queryText are aliases, as are category and categoryFilters. Category
   * values may be repeated or comma separated.
   */
  @GetMapping("/products")
  public SearchResponseDTO searchProducts(
      @RequestParam(name = "q", required = false) String q,
      @RequestParam(name = "queryText", required = false) String queryText,
      @RequestParam(name = "category", required = false) List<String> category,
      @RequestParam(name = "categoryFilters", required = false) List<String> categoryFilters,
      @RequestParam(name = "sortField", defaultValue = "name") String sortField,
      @RequestParam(name = "sortOrder", defaultValue = "asc") String sortOrder,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size) {
    List<String> categories = new ArrayList<>();
    if (category != null) {
      categories.addAll(category);
    }
    if (categoryFilters != null) {
      categories.addAll(categoryFilters);
    }
    Map<String, List<String>> filters =
        categories.isEmpty() ? Map.of() : Map.of(SearchQuery.CATEGORY_FILTER, categories);
    String text = q != null ? q : queryText;
    return searchQueryService.executeSearch(
        new SearchQuery(text, filters, sortField, sortOrder, page, size));
  }

  /** Upserts a product document. Accepts both summary and details payloads. */
  @PostMapping("/index")
  public ResponseEntity<BaseResponseDTO> indexProduct(@RequestBody ProductSummaryDTO product) {
    ProductSearchIndex document = productDataIndexer.updateIndex(product);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(BaseResponseDTO.created("Product " + document.getProductId() + " indexed"));
  }
}
