package com.ecommerce.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.common.dto.ProductCreationDTO;
import com.ecommerce.common.dto.ProductDetailsDTO;
import com.ecommerce.common.dto.ProductSummaryDTO;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.product.model.Inventory;
import com.ecommerce.product.model.Product;
import com.ecommerce.product.repository.InventoryRepository;
import com.ecommerce.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ProductCatalogServiceTest {

  private ProductRepository productRepository;
  private InventoryRepository inventoryRepository;
  private CategoryService categoryService;
  private SearchIndexClient searchIndexClient;
  private ProductCatalogService service;

  @BeforeEach
  void setUp() {
    productRepository = mock(ProductRepository.class);
    inventoryRepository = mock(InventoryRepository.class);
    categoryService = mock(CategoryService.class);
    searchIndexClient = mock(SearchIndexClient.class);
    service =
        new ProductCatalogService(
            productRepository,
            inventoryRepository,
            categoryService,
            searchIndexClient,
            new TransactionTemplate(mock(PlatformTransactionManager.class)));
    when(categoryService.exists("ELECTRONICS")).thenReturn(true);
    when(categoryService.exists("electronics")).thenReturn(true);
    when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));
    when(inventoryRepository.save(any(Inventory.class))).thenAnswer(i -> i.getArgument(0));
  }

  private static ProductCreationDTO request(String sku, BigDecimal price, Integer stock) {
    return new ProductCreationDTO(
        sku, "Phone", "A phone", price, "ELECTRONICS", stock, "http://img/phone.png");
  }

  private static void assertCode(Runnable call, String code) {
    assertThatThrownBy(call::run)
        .isInstanceOf(ApplicationBaseException.class)
        .extracting("errorCode")
        .isEqualTo(code);
  }

  @Test
  void getProductDetails_existingSku_mergesInventory() {
    when(productRepository.findById("P1"))
        .thenReturn(Optional.of(new Product("P1", "Phone", "d", BigDecimal.TEN, "ELECTRONICS", null, null)));
    when(inventoryRepository.findById("P1")).thenReturn(Optional.of(new Inventory("P1", 7, 2)));

    ProductDetailsDTO details = service.getProductDetails("P1");

    assertThat(details.getSku()).isEqualTo("P1");
    assertThat(details.getCurrentStock()).isEqualTo(7);
    assertThat(details.getReservedStock()).isEqualTo(2);
    assertThat(details.isSuccess()).isTrue();
  }

  @Test
  void getProductDetails_missingInventory_reportsZeroStock() {
    when(productRepository.findById("P1"))
        .thenReturn(Optional.of(new Product("P1", "Phone", "d", BigDecimal.TEN, "ELECTRONICS", null, null)));
    when(inventoryRepository.findById("P1")).thenReturn(Optional.empty());

    ProductDetailsDTO details = service.getProductDetails("P1");

    assertThat(details.getCurrentStock()).isZero();
    assertThat(details.getReservedStock()).isZero();
  }

  @Test
  void getProductDetails_unknownSku_throwsProd4001() {
    when(productRepository.findById("NOPE")).thenReturn(Optional.empty());
    assertCode(() -> service.getProductDetails("NOPE"), "PROD_4001");
  }

  @Test
  void getProductDetails_blankSku_throwsProd4001() {
    assertCode(() -> service.getProductDetails(" "), "PROD_4001");
  }

  @Test
  void createProduct_valid_savesProductAndInventoryAndPushesToSearch() {
    ProductDetailsDTO created = service.createProduct(request("P1", new BigDecimal("99.99"), 5));

    assertThat(created.getSku()).isEqualTo("P1");
    assertThat(created.getCurrentStock()).isEqualTo(5);
    assertThat(created.getStatusCode()).isEqualTo(201);
    ArgumentCaptor<ProductSummaryDTO> summary = ArgumentCaptor.forClass(ProductSummaryDTO.class);
    verify(searchIndexClient).pushProduct(summary.capture());
    assertThat(summary.getValue().getSku()).isEqualTo("P1");
    assertThat(summary.getValue().getImageUrl()).isEqualTo("http://img/phone.png");
  }

  @Test
  void createProduct_blankSkuAndNullStock_generatesSkuAndZeroStock() {
    ProductCreationDTO dto = request(null, BigDecimal.ONE, null);
    dto.setCategoryId("electronics");

    ProductDetailsDTO created = service.createProduct(dto);

    assertThat(created.getSku()).startsWith("SKU-");
    assertThat(created.getCurrentStock()).isZero();
    assertThat(created.getCategoryId()).isEqualTo("ELECTRONICS");
  }

  @Test
  void createProduct_searchUnavailable_stillSucceeds() {
    when(searchIndexClient.pushProduct(any())).thenReturn(false);
    assertThat(service.createProduct(request("P1", BigDecimal.ONE, 1)).getSku()).isEqualTo("P1");
  }

  @Test
  void createProduct_duplicateSku_throwsProd4004() {
    when(productRepository.existsById("P1")).thenReturn(true);
    assertCode(() -> service.createProduct(request("P1", BigDecimal.ONE, 1)), "PROD_4004");
    verify(searchIndexClient, never()).pushProduct(any());
  }

  @Test
  void createProduct_unknownCategory_throwsProd4003() {
    ProductCreationDTO dto = request("P1", BigDecimal.ONE, 1);
    dto.setCategoryId("TOYS");
    assertCode(() -> service.createProduct(dto), "PROD_4003");
  }

  @Test
  void createProduct_nonPositivePrice_throwsProd4005() {
    assertCode(() -> service.createProduct(request("P1", BigDecimal.ZERO, 1)), "PROD_4005");
    assertCode(() -> service.createProduct(request("P1", null, 1)), "PROD_4005");
  }

  @Test
  void createProduct_negativeStock_throwsProd4007() {
    assertCode(() -> service.createProduct(request("P1", BigDecimal.ONE, -1)), "PROD_4007");
  }

  @Test
  void createProduct_invalidNameOrDescription_throwsProd4010() {
    ProductCreationDTO longName = request("P1", BigDecimal.ONE, 1);
    longName.setName("x".repeat(ProductCatalogService.MAX_NAME_LENGTH + 1));
    assertCode(() -> service.createProduct(longName), "PROD_4010");

    ProductCreationDTO blankName = request("P1", BigDecimal.ONE, 1);
    blankName.setName(" ");
    assertCode(() -> service.createProduct(blankName), "PROD_4010");

    ProductCreationDTO longDescription = request("P1", BigDecimal.ONE, 1);
    longDescription.setDescription("x".repeat(ProductCatalogService.MAX_DESCRIPTION_LENGTH + 1));
    assertCode(() -> service.createProduct(longDescription), "PROD_4010");

    assertCode(() -> service.createProduct(null), "PROD_4010");
  }

  @Test
  void updateProduct_existing_updatesFieldsStockAndPushesToSearch() {
    when(productRepository.findById("P1"))
        .thenReturn(Optional.of(new Product("P1", "Old", "old", BigDecimal.ONE, "BOOKS", null, null)));
    when(inventoryRepository.findById("P1")).thenReturn(Optional.of(new Inventory("P1", 3, 1)));

    ProductDetailsDTO updated = service.updateProduct("P1", request("ignored", BigDecimal.TEN, 9));

    assertThat(updated.getName()).isEqualTo("Phone");
    assertThat(updated.getPrice()).isEqualTo(BigDecimal.TEN);
    assertThat(updated.getCategoryId()).isEqualTo("ELECTRONICS");
    assertThat(updated.getCurrentStock()).isEqualTo(9);
    assertThat(updated.getReservedStock()).isEqualTo(1);
    verify(searchIndexClient).pushProduct(any());
  }

  @Test
  void updateProduct_nullStockAndNoInventoryRow_keepsZeroStock() {
    when(productRepository.findById("P1"))
        .thenReturn(Optional.of(new Product("P1", "Old", "old", BigDecimal.ONE, "BOOKS", null, null)));
    when(inventoryRepository.findById("P1")).thenReturn(Optional.empty());

    ProductDetailsDTO updated = service.updateProduct("P1", request(null, BigDecimal.TEN, null));

    assertThat(updated.getCurrentStock()).isZero();
  }

  @Test
  void updateProduct_stockBelowReserved_throwsProd4007() {
    when(productRepository.findById("P1"))
        .thenReturn(Optional.of(new Product("P1", "Old", "old", BigDecimal.ONE, "BOOKS", null, null)));
    when(inventoryRepository.findById("P1")).thenReturn(Optional.of(new Inventory("P1", 5, 4)));

    assertCode(() -> service.updateProduct("P1", request(null, BigDecimal.TEN, 3)), "PROD_4007");
  }

  @Test
  void updateProduct_unknownSku_throwsProd4001() {
    when(productRepository.findById("NOPE")).thenReturn(Optional.empty());
    assertCode(() -> service.updateProduct("NOPE", request(null, BigDecimal.TEN, 1)), "PROD_4001");
  }

  @Test
  void listAllProducts_returnsEveryProductWithStock() {
    when(productRepository.findAll())
        .thenReturn(
            List.of(
                new Product("A", "a", null, BigDecimal.ONE, "BOOKS", null, null),
                new Product("B", "b", null, BigDecimal.ONE, "BOOKS", null, null)));
    when(inventoryRepository.findById("A")).thenReturn(Optional.of(new Inventory("A", 4, 0)));
    when(inventoryRepository.findById("B")).thenReturn(Optional.empty());

    List<ProductDetailsDTO> products = service.listAllProducts();

    assertThat(products).extracting(ProductDetailsDTO::getSku).containsExactly("A", "B");
    assertThat(products).extracting(ProductDetailsDTO::getCurrentStock).containsExactly(4, 0);
  }
}
