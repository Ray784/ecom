package com.ecommerce.common.dto;

import java.util.Objects;

/** Metadata describing one page of a paginated result. */
public class PaginationDTO {

  private int page;
  private int size;
  private long totalElements;
  private int totalPages;

  /** Creates an empty instance for JSON deserialization. */
  public PaginationDTO() {}

  /** Creates an instance with all fields populated. */
  public PaginationDTO(int page, int size, long totalElements, int totalPages) {
    this.page = page;
    this.size = size;
    this.totalElements = totalElements;
    this.totalPages = totalPages;
  }

  /**
   * Builds pagination metadata and derives the total page count by rounding up.
   *
   * <p>A non positive page size yields zero total pages.
   */
  public static PaginationDTO of(int page, int size, long totalElements) {
    int totalPages = size <= 0 ? 0 : (int) ((totalElements + size - 1) / size);
    return new PaginationDTO(page, size, totalElements, totalPages);
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

  public long getTotalElements() {
    return totalElements;
  }

  public void setTotalElements(long totalElements) {
    this.totalElements = totalElements;
  }

  public int getTotalPages() {
    return totalPages;
  }

  public void setTotalPages(int totalPages) {
    this.totalPages = totalPages;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PaginationDTO that = (PaginationDTO) o;
    return page == that.page
        && size == that.size
        && totalElements == that.totalElements
        && totalPages == that.totalPages;
  }

  @Override
  public int hashCode() {
    return Objects.hash(page, size, totalElements, totalPages);
  }

  @Override
  public String toString() {
    return "PaginationDTO{page=" + page + ", size=" + size + ", totalElements=" + totalElements
        + ", totalPages=" + totalPages + "}";
  }
}
