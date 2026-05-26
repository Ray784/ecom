package com.ecommerce.search.service;

import com.ecommerce.common.dto.PaginationDTO;
import com.ecommerce.common.dto.ProductSummaryDTO;
import com.ecommerce.common.dto.SearchResponseDTO;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.search.model.ProductSearchIndex;
import com.ecommerce.search.model.SearchQuery;
import com.ecommerce.search.repository.ProductIndexStore;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Executes text search with category filtering, sorting and pagination.
 *
 * <p>Every whitespace separated term must appear in the name, description, category or keywords
 * of a product for it to match. An empty query matches every product.
 */
@Service
public class SearchQueryService {

  /** Fields that results may be sorted by. */
  public static final Set<String> SORT_FIELDS = Set.of("name", "price");

  private static final Pattern CATEGORY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

  private final ProductIndexStore store;
  private final int minQueryLength;
  private final int maxPageSize;

  public SearchQueryService(
      ProductIndexStore store,
      @Value("${search.min-query-length:2}") int minQueryLength,
      @Value("${search.max-page-size:100}") int maxPageSize) {
    this.store = store;
    this.minQueryLength = minQueryLength;
    this.maxPageSize = maxPageSize;
  }

  /** Validates the query and returns one page of matching products. */
  public SearchResponseDTO executeSearch(SearchQuery query) {
    SearchQuery effective = query == null ? new SearchQuery() : query;
    validate(effective);
    try {
      List<String> terms = terms(effective.getTextQuery());
      List<ProductSummaryDTO> matches =
          loadDocuments().stream()
              .filter(document -> matchesAll(document, terms))
              .map(ProductDataIndexer::toSummary)
              .toList();
      List<ProductSummaryDTO> sorted = filterAndSortResults(matches, effective);
      int from = (int) Math.min((long) effective.getPage() * effective.getSize(), sorted.size());
      int to = Math.min(from + effective.getSize(), sorted.size());
      SearchResponseDTO response =
          new SearchResponseDTO(
              PaginationDTO.of(effective.getPage(), effective.getSize(), sorted.size()),
              List.copyOf(sorted.subList(from, to)));
      response.setSuccess(true);
      response.setStatusCode(200);
      response.setMessage("Found " + sorted.size() + " products");
      return response;
    } catch (ApplicationBaseException e) {
      throw e;
    } catch (RuntimeException e) {
      throw CodedErrorFactory.create(MappedError.SEARCH_5003, e, e.getMessage());
    }
  }

  /** Applies the category filter and the requested ordering. Ties are broken by SKU. */
  public List<ProductSummaryDTO> filterAndSortResults(
      List<ProductSummaryDTO> results, SearchQuery query) {
    Set<String> categories =
        query.categoryFilters().stream()
            .map(c -> c.trim().toUpperCase(Locale.ROOT))
            .collect(Collectors.toSet());
    Comparator<ProductSummaryDTO> order = comparatorFor(query.getSortField());
    if ("desc".equalsIgnoreCase(query.getSortOrder())) {
      order = order.reversed();
    }
    order = order.thenComparing(ProductSummaryDTO::getSku, Comparator.nullsLast(Comparator.naturalOrder()));
    return results.stream()
        .filter(
            p ->
                categories.isEmpty()
                    || (p.getCategoryId() != null
                        && categories.contains(p.getCategoryId().toUpperCase(Locale.ROOT))))
        .sorted(order)
        .toList();
  }

  private List<ProductSearchIndex> loadDocuments() {
    try {
      return store.findAll();
    } catch (IllegalStateException e) {
      throw CodedErrorFactory.create(MappedError.SEARCH_5001, e);
    }
  }

  private void validate(SearchQuery query) {
    String text = query.getTextQuery();
    if (text != null && !text.isBlank() && text.trim().length() < minQueryLength) {
      throw CodedErrorFactory.create(MappedError.SEARCH_4001, minQueryLength);
    }
    if (query.getSortField() == null
        || !SORT_FIELDS.contains(query.getSortField().toLowerCase(Locale.ROOT))) {
      throw CodedErrorFactory.create(MappedError.SEARCH_4002, query.getSortField());
    }
    if (!"asc".equalsIgnoreCase(query.getSortOrder())
        && !"desc".equalsIgnoreCase(query.getSortOrder())) {
      throw CodedErrorFactory.create(MappedError.SEARCH_4002, query.getSortOrder());
    }
    for (String key : query.getFilters().keySet()) {
      if (!SearchQuery.CATEGORY_FILTER.equals(key)) {
        throw CodedErrorFactory.create(MappedError.SEARCH_4003, key);
      }
    }
    for (String category : query.categoryFilters()) {
      if (category == null || !CATEGORY_PATTERN.matcher(category.trim()).matches()) {
        throw CodedErrorFactory.create(MappedError.SEARCH_4003, category);
      }
    }
    if (query.getPage() < 0 || query.getSize() < 1 || query.getSize() > maxPageSize) {
      throw CodedErrorFactory.create(MappedError.SEARCH_4004);
    }
  }

  private static Comparator<ProductSummaryDTO> comparatorFor(String sortField) {
    if ("price".equalsIgnoreCase(sortField)) {
      return nullsLast(ProductSummaryDTO::getPrice, Comparator.<BigDecimal>naturalOrder());
    }
    return nullsLast(ProductSummaryDTO::getName, String.CASE_INSENSITIVE_ORDER);
  }

  private static <T> Comparator<ProductSummaryDTO> nullsLast(
      Function<ProductSummaryDTO, T> key, Comparator<T> comparator) {
    return Comparator.comparing(key, Comparator.nullsLast(comparator));
  }

  private static List<String> terms(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    return List.of(text.trim().toLowerCase(Locale.ROOT).split("\\s+"));
  }

  private static boolean matchesAll(ProductSearchIndex document, List<String> terms) {
    if (terms.isEmpty()) {
      return true;
    }
    String haystack =
        String.join(
                " ",
                String.valueOf(document.getName()),
                String.valueOf(document.getDescription()),
                String.valueOf(document.getCategoryId()),
                String.join(" ", document.getKeywords()))
            .toLowerCase(Locale.ROOT);
    return terms.stream().allMatch(haystack::contains);
  }
}
