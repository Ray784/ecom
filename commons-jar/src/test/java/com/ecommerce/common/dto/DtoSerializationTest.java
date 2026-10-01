package com.ecommerce.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DtoSerializationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private static final ItemDetails ITEM = new ItemDetails("SKU-1", 2, new BigDecimal("10.50"));

  static Stream<Object> populatedDtos() {
    AddressDTO address = new AddressDTO(7L, "1 Main St", "Springfield", "12345", true);
    ProductSummaryDTO summary =
        new ProductSummaryDTO("SKU-1", "Mug", "Ceramic mug", new BigDecimal("9.99"), "cat-1", "http://img/1");
    BaseResponseDTO created = BaseResponseDTO.created("made");
    return Stream.of(
        created,
        new ErrorResponseDTO(404, "PROD_4001", "missing", "2026-10-10T00:00:00Z"),
        new PaginationDTO(1, 20, 41L, 3),
        ITEM,
        new OrderPlacedEvent("o-1", "u-1", List.of(ITEM), new BigDecimal("21.00")),
        new InventoryReservationRequest("o-1", List.of(ITEM)),
        new InventoryReservedEvent("o-1", true, "reserved"),
        new PaymentResultEvent("o-1", "tx-1", false, "declined"),
        new LoginRequestDTO("alice", "s3cret"),
        new RegistrationDTO("alice", "s3cret", "a@x.com", "Alice", "ROLE_ADMIN"),
        new AuthResponseDTO("jwt", 1_700_000_000_000L, "u-1", "alice"),
        new UserDetailsDTO("u-1", "alice", Set.of("ROLE_CUSTOMER"), "a@x.com", true),
        new ProfileUpdateDTO("Alice", "Smith", "a@x.com"),
        address,
        new UserProfileDTO("u-1", "Alice", "Smith", "a@x.com", List.of(address)),
        new ProductCreationDTO("SKU-1", "Mug", "Ceramic mug", new BigDecimal("9.99"), "cat-1", 5, "http://img/1"),
        new ProductDetailsDTO("SKU-1", "Mug", "Ceramic mug", new BigDecimal("9.99"), "cat-1", 5, 1, new BigDecimal("4.5")),
        summary,
        new CartItemDTO("SKU-1", 2, new BigDecimal("9.99")),
        new CartDTO("u-1", List.of(new CartItemDTO("SKU-1", 2, new BigDecimal("9.99"))), new BigDecimal("19.98")),
        new OrderItemDTO("SKU-1", 2, new BigDecimal("9.99")),
        new OrderDetailsDTO("o-1", "u-1", "PENDING", new BigDecimal("19.98"), "1 Main St",
            List.of(new OrderItemDTO("SKU-1", 2, new BigDecimal("9.99"))), "2026-10-10T00:00:00Z"),
        new ReviewDTO(3L, "SKU-1", 5, "Great", "u-1", "2026-10-10"),
        new RatingSummaryDTO("SKU-1", new BigDecimal("4.50"), 2L),
        new SearchRequestDTO("mug", List.of("cat-1"), "price", "asc", 2, 25),
        new SearchResponseDTO(PaginationDTO.of(0, 10, 1L), List.of(summary)),
        new InvoiceDTO("inv-1", "o-1", "file:///inv-1.pdf", new BigDecimal("100.00"), new BigDecimal("5.00"),
            new BigDecimal("9.50"), new BigDecimal("104.50"), "2026-10-10"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("populatedDtos")
  void jsonRoundTripPreservesEveryField(Object dto) throws Exception {
    String json = mapper.writeValueAsString(dto);

    Object restored = mapper.readValue(json, dto.getClass());

    assertThat(restored).isEqualTo(dto).hasSameHashCodeAs(dto);
  }

  @Test
  void responseDtosSerializeInheritedBaseFields() throws Exception {
    JsonNode node = mapper.readTree(mapper.writeValueAsString(new CartDTO("u-1", List.of(), BigDecimal.ZERO)));

    assertThat(node.get("success").asBoolean()).isTrue();
    assertThat(node.get("statusCode").asInt()).isEqualTo(200);
    assertThat(node.get("message").asText()).isEqualTo("Success");
    assertThat(node.get("userId").asText()).isEqualTo("u-1");
  }

  @Test
  void responseDtosWithDifferentBaseStatusAreNotEqual() {
    CartDTO ok = new CartDTO("u-1", List.of(), BigDecimal.ZERO);
    CartDTO created = new CartDTO("u-1", List.of(), BigDecimal.ZERO);
    created.setStatusCode(201);

    assertThat(ok).isNotEqualTo(created);
    assertThat(ok).isNotEqualTo(BaseResponseDTO.ok("Success"));
  }

  @Test
  void addressDefaultFlagSerializesAsIsDefault() throws Exception {
    JsonNode node = mapper.readTree(mapper.writeValueAsString(new AddressDTO(1L, "s", "c", "z", true)));

    assertThat(node.get("isDefault").asBoolean()).isTrue();
    assertThat(node.has("default")).isFalse();
    assertThat(mapper.readValue("{\"isDefault\":true}", AddressDTO.class).isDefault()).isTrue();
  }

  @Test
  void kafkaEventsIgnoreUnknownProperties() throws Exception {
    String json = "{\"orderId\":\"o-1\",\"success\":true,\"message\":\"ok\",\"futureField\":1}";

    InventoryReservedEvent event = mapper.readValue(json, InventoryReservedEvent.class);

    assertThat(event).isEqualTo(new InventoryReservedEvent("o-1", true, "ok"));
  }

  @Test
  void responseDtosIgnoreUnknownPropertiesThroughBaseClass() throws Exception {
    ReviewDTO review = mapper.readValue("{\"productId\":\"SKU-1\",\"extra\":true}", ReviewDTO.class);

    assertThat(review.getProductId()).isEqualTo("SKU-1");
  }

  @Test
  void loginRequestToStringMasksPassword() {
    assertThat(new LoginRequestDTO("alice", "s3cret").toString()).doesNotContain("s3cret").contains("password=****");
    assertThat(new RegistrationDTO("alice", "s3cret", null, null, null).toString()).doesNotContain("s3cret");
  }

  @Test
  void registrationRoleDefaultsToCustomer() throws Exception {
    assertThat(new RegistrationDTO().getRole()).isEqualTo(RegistrationDTO.DEFAULT_ROLE);
    assertThat(new RegistrationDTO("a", "p", "e", "f", null).getRole()).isEqualTo("ROLE_CUSTOMER");
    assertThat(mapper.readValue("{\"username\":\"a\"}", RegistrationDTO.class).getRole()).isEqualTo("ROLE_CUSTOMER");
  }

  @Test
  void searchRequestSizeDefaultsToTen() throws Exception {
    SearchRequestDTO request = mapper.readValue("{\"queryText\":\"mug\"}", SearchRequestDTO.class);

    assertThat(request.getSize()).isEqualTo(SearchRequestDTO.DEFAULT_SIZE).isEqualTo(10);
    assertThat(request.getPage()).isZero();
  }

  @Test
  void singleItemReservationRequestWrapsOneItem() {
    InventoryReservationRequest request = new InventoryReservationRequest("o-1", "SKU-1", 3);

    assertThat(request.getOrderId()).isEqualTo("o-1");
    assertThat(request.getItems()).containsExactly(new ItemDetails("SKU-1", 3, null));
  }

  @Test
  void paginationOfRoundsTotalPagesUp() {
    assertThat(PaginationDTO.of(0, 10, 41L)).isEqualTo(new PaginationDTO(0, 10, 41L, 5));
    assertThat(PaginationDTO.of(0, 10, 40L).getTotalPages()).isEqualTo(4);
    assertThat(PaginationDTO.of(0, 10, 0L).getTotalPages()).isZero();
  }

  @Test
  void paginationOfWithNonPositiveSizeHasZeroPages() {
    assertThat(PaginationDTO.of(0, 0, 5L).getTotalPages()).isZero();
    assertThat(PaginationDTO.of(0, -1, 5L).getTotalPages()).isZero();
  }

  @Test
  void baseResponseFactoriesSetOutcomeAndStatus() {
    assertThat(BaseResponseDTO.ok("fine")).isEqualTo(new BaseResponseDTO(true, 200, "fine"));
    assertThat(BaseResponseDTO.created("made")).isEqualTo(new BaseResponseDTO(true, 201, "made"));
    assertThat(BaseResponseDTO.failure(409, "dup")).isEqualTo(new BaseResponseDTO(false, 409, "dup"));
  }

  @Test
  void errorResponseOfStampsCurrentIsoTimestamp() {
    Instant before = Instant.now();

    ErrorResponseDTO error = ErrorResponseDTO.of(400, "IAM_4005", "weak");

    assertThat(error.getHttpStatusCode()).isEqualTo(400);
    assertThat(error.getErrorCode()).isEqualTo("IAM_4005");
    assertThat(error.getErrorDetails()).isEqualTo("weak");
    assertThat(Instant.parse(error.getTimestamp())).isBetween(before, Instant.now());
  }
}
