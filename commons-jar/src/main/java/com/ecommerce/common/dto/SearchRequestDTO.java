package com.ecommerce.common.dto;

import java.util.List;
import java.util.Objects;

/** Parameters for executing a product search. */
public class SearchRequestDTO {

  /** Page size used when the request does not specify one. */
  public static final int DEFAULT_SIZE = 10;

  private String queryText;
  private List<String> categoryFilters;
  private String sortField;
  private String sortOrder;
  private int page;
  private int size = DEFAULT_SIZE;

  /** Creates an empty instance for JSON deserialization. */
  public SearchRequestDTO() {}

  /** Creates an instance with all fields populated. */
  public SearchRequestDTO(String queryText, List<String> categoryFilters, String sortField, String sortOrder, int page, int size) {
    this.queryText = queryText;
    this.categoryFilters = categoryFilters;
    this.sortField = sortField;
    this.sortOrder = sortOrder;
    this.page = page;
    this.size = size;
  }

  public String getQueryText() {
    return queryText;
  }

  public void setQueryText(String queryText) {
    this.queryText = queryText;
  }

  public List<String> getCategoryFilters() {
    return categoryFilters;
  }

  public void setCategoryFilters(List<String> categoryFilters) {
    this.categoryFilters = categoryFilters;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SearchRequestDTO that = (SearchRequestDTO) o;
    return Objects.equals(queryText, that.queryText)
        && Objects.equals(categoryFilters, that.categoryFilters)
        && Objects.equals(sortField, that.sortField)
        && Objects.equals(sortOrder, that.sortOrder)
        && page == that.page
        && size == that.size;
  }

  @Override
  public int hashCode() {
    return Objects.hash(queryText, categoryFilters, sortField, sortOrder, page, size);
  }

  @Override
  public String toString() {
    return "SearchRequestDTO{" + "queryText=" + queryText
        + ", categoryFilters=" + categoryFilters
        + ", sortField=" + sortField
        + ", sortOrder=" + sortOrder
        + ", page=" + page
        + ", size=" + size + "}";
  }
}
