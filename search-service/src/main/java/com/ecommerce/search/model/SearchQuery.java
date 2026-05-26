package com.ecommerce.search.model;

import java.util.List;
import java.util.Map;

/**
 * Normalized search request.
 *
 * <p>Filters map a field name to accepted values. The only supported filter key is categoryId.
 */
public class SearchQuery {

  /** Filter key for category restrictions. */
  public static final String CATEGORY_FILTER = "categoryId";

  private String textQuery;
  private Map<String, List<String>> filters;
  private String sortField;
  private String sortOrder;
  private int page;
  private int size;

  public SearchQuery() {
    this(null, Map.of(), "name", "asc", 0, 10);
  }

  public SearchQuery(
      String textQuery,
      Map<String, List<String>> filters,
      String sortField,
      String sortOrder,
      int page,
      int size) {
    this.textQuery = textQuery;
    this.filters = filters == null ? Map.of() : filters;
    this.sortField = sortField;
    this.sortOrder = sortOrder;
    this.page = page;
    this.size = size;
  }

  /** Returns the category filter values or an empty list. */
  public List<String> categoryFilters() {
    List<String> values = filters.get(CATEGORY_FILTER);
    return values == null ? List.of() : values;
  }

  public String getTextQuery() {
    return textQuery;
  }

  public void setTextQuery(String textQuery) {
    this.textQuery = textQuery;
  }

  public Map<String, List<String>> getFilters() {
    return filters;
  }

  public void setFilters(Map<String, List<String>> filters) {
    this.filters = filters == null ? Map.of() : filters;
  }

  public String getSortField() {
    return sortField;
  }

  public void setSortField(String sortField) {
    this.sortField = sortField;
  }

  public String getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(String sortOrder) {
    this.sortOrder = sortOrder;
  }

  public int getPage() {
    return page;
  }

  public void setPage(int page) {
    this.page = page;
  }

  public int getSize() {
    return size;
  }

  public void setSize(int size) {
    this.size = size;
  }
}
