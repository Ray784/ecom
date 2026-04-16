package com.ecommerce.common.dto;

import java.util.List;
import java.util.Objects;

/** Search results together with pagination metadata. */
public class SearchResponseDTO extends BaseResponseDTO {

  private PaginationDTO pagination;
  private List<ProductSummaryDTO> results;

  /** Creates an empty instance for JSON deserialization. */
  public SearchResponseDTO() {}

  /** Creates a successful response with status 200 carrying the given payload fields. */
  public SearchResponseDTO(PaginationDTO pagination, List<ProductSummaryDTO> results) {
    super(true, 200, "Success");
    this.pagination = pagination;
    this.results = results;
  }

  public PaginationDTO getPagination() {
    return pagination;
  }

  public void setPagination(PaginationDTO pagination) {
    this.pagination = pagination;
  }

  public List<ProductSummaryDTO> getResults() {
    return results;
  }

  public void setResults(List<ProductSummaryDTO> results) {
    this.results = results;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!super.equals(o)) {
      return false;
    }
    SearchResponseDTO that = (SearchResponseDTO) o;
    return Objects.equals(pagination, that.pagination)
        && Objects.equals(results, that.results);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), pagination, results);
  }

  @Override
  public String toString() {
    return "SearchResponseDTO{" + "pagination=" + pagination
        + ", results=" + results
        + ", " + super.toString() + "}";
  }
}
