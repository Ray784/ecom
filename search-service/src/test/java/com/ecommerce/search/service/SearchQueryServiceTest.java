package com.ecommerce.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ecommerce.common.dto.ProductSummaryDTO;
import com.ecommerce.common.dto.SearchResponseDTO;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.search.model.SearchQuery;
import com.ecommerce.search.repository.ProductIndexStore;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchQueryServiceTest {

  private SearchQueryService service;

  @BeforeEach
  void setUp() {
    ProductIndexStore store = new ProductIndexStore();
    ProductDataIndexer indexer = new ProductDataIndexer(store);
    indexer.updateIndex(new ProductSummaryDTO("E1", "Laptop Pro", "fast laptop", new BigDecimal("1500"), "ELECTRONICS", null));
    indexer.updateIndex(new ProductSummaryDTO("E2", "budget laptop", "cheap", new BigDecimal("300"), "ELECTRONICS", null));
    indexer.updateIndex(new ProductSummaryDTO("B1", "Java Book", "learn java", new BigDecimal("40"), "BOOKS", null));
    indexer.updateIndex(new ProductSummaryDTO("C1", "Shirt", null, null, "CLOTHING", null));
    service = new SearchQueryService(store, 2, 100);
  }

  private static SearchQuery query(String text, List<String> categories, String field, String order, int page, int size) {
    Map<String, List<String>> filters =
        categories == null ? Map.of() : Map.of(SearchQuery.CATEGORY_FILTER, categories);
    return new SearchQuery(text, filters, field, order, page, size);
  }

  private static List<String> skus(SearchResponseDTO response) {
    return response.getResults().stream().map(ProductSummaryDTO::getSku).toList();
  }

  private void assertCode(SearchQuery q, String code) {
    assertThatThrownBy(() -> service.executeSearch(q))
        .isInstanceOf(ApplicationBaseException.class)
        .extracting("errorCode")
        .isEqualTo(code);
  }

  @Test
  void executeSearch_textQuery_matchesAllTermsCaseInsensitively() {
    SearchResponseDTO response = service.executeSearch(query("LAPTOP", null, "name", "asc", 0, 10));

    assertThat(skus(response)).containsExactly("E2", "E1");
    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getPagination().getTotalElements()).isEqualTo(2);
    assertThat(skus(service.executeSearch(query("fast laptop", null, "name", "asc", 0, 10)))).containsExactly("E1");
  }

  @Test
  void executeSearch_emptyOrNullQuery_returnsEverything() {
    assertThat(service.executeSearch(query("  ", null, "name", "asc", 0, 10)).getResults()).hasSize(4);
    assertThat(service.executeSearch(null).getResults()).hasSize(4);
  }

  @Test
  void executeSearch_categoryFilter_isCaseInsensitive() {
    SearchResponseDTO response = service.executeSearch(query(null, List.of("books", "clothing"), "name", "asc", 0, 10));
    assertThat(skus(response)).containsExactly("B1", "C1");
  }

  @Test
  void executeSearch_sortByPrice_descendingPutsNullPriceFirstAscendingLast() {
    assertThat(skus(service.executeSearch(query(null, null, "price", "asc", 0, 10))))
        .containsExactly("B1", "E2", "E1", "C1");
    assertThat(skus(service.executeSearch(query(null, null, "PRICE", "DESC", 0, 10))))
        .containsExactly("C1", "E1", "E2", "B1");
  }

  @Test
  void executeSearch_pagination_slicesResultsAndReportsTotals() {
    SearchResponseDTO page1 = service.executeSearch(query(null, null, "price", "asc", 1, 3));
    assertThat(skus(page1)).containsExactly("C1");
    assertThat(page1.getPagination().getTotalPages()).isEqualTo(2);

    assertThat(service.executeSearch(query(null, null, "price", "asc", 5, 3)).getResults()).isEmpty();
  }

  @Test
  void executeSearch_shortQuery_throwsSearch4001() {
    assertCode(query("a", null, "name", "asc", 0, 10), "SEARCH_4001");
  }

  @Test
  void executeSearch_invalidSortFieldOrOrder_throwsSearch4002() {
    assertCode(query(null, null, "rating", "asc", 0, 10), "SEARCH_4002");
    assertCode(query(null, null, null, "asc", 0, 10), "SEARCH_4002");
    assertCode(query(null, null, "name", "up", 0, 10), "SEARCH_4002");
  }

  @Test
  void executeSearch_invalidCategoryFilter_throwsSearch4003() {
    assertCode(query(null, List.of("bad value!"), "name", "asc", 0, 10), "SEARCH_4003");
    assertCode(query(null, Arrays.asList((String) null), "name", "asc", 0, 10), "SEARCH_4003");
    assertCode(
        new SearchQuery(null, Map.of("brand", List.of("x")), "name", "asc", 0, 10), "SEARCH_4003");
  }

  @Test
  void executeSearch_invalidPagination_throwsSearch4004() {
    assertCode(query(null, null, "name", "asc", -1, 10), "SEARCH_4004");
    assertCode(query(null, null, "name", "asc", 0, 0), "SEARCH_4004");
    assertCode(query(null, null, "name", "asc", 0, 101), "SEARCH_4004");
  }

  @Test
  void executeSearch_indexUnavailable_throwsSearch5001() {
    ProductIndexStore store = mock(ProductIndexStore.class);
    when(store.findAll()).thenThrow(new IllegalStateException("offline"));

    assertThatThrownBy(() -> new SearchQueryService(store, 2, 100).executeSearch(new SearchQuery()))
        .extracting("errorCode")
        .isEqualTo("SEARCH_5001");
  }

  @Test
  void executeSearch_unexpectedFailure_throwsSearch5003() {
    ProductIndexStore store = mock(ProductIndexStore.class);
    when(store.findAll()).thenThrow(new UnsupportedOperationException("boom"));

    assertThatThrownBy(() -> new SearchQueryService(store, 2, 100).executeSearch(new SearchQuery()))
        .extracting("errorCode")
        .isEqualTo("SEARCH_5003");
  }

  @Test
  void filterAndSortResults_nameSortTieBreaksBySkuAndSkipsNullCategory() {
    List<ProductSummaryDTO> input =
        List.of(
            new ProductSummaryDTO("Z", "same", null, null, "BOOKS", null),
            new ProductSummaryDTO("A", "Same", null, null, "BOOKS", null),
            new ProductSummaryDTO("N", null, null, null, null, null));

    assertThat(service.filterAndSortResults(input, query(null, null, "name", "asc", 0, 10)))
        .extracting(ProductSummaryDTO::getSku)
        .containsExactly("A", "Z", "N");
    assertThat(service.filterAndSortResults(input, query(null, List.of("BOOKS"), "name", "asc", 0, 10)))
        .extracting(ProductSummaryDTO::getSku)
        .containsExactly("A", "Z");
  }
}
