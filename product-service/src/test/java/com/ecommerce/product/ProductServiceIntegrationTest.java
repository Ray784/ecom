package com.ecommerce.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.common.dto.InventoryReservationRequest;
import com.ecommerce.common.dto.ProductCreationDTO;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.product.repository.CategoryRepository;
import com.ecommerce.product.repository.InventoryRepository;
import com.ecommerce.product.service.InventoryManagerService;
import com.ecommerce.product.service.ProductCatalogService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Boots the full context against H2 to verify seeding, JPA mappings and optimistic locking. */
@SpringBootTest(
    properties = {
      "product.kafka.listener-auto-startup=false",
      "product.search.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:product-it;MODE=MySQL;DB_CLOSE_DELAY=-1"
    })
class ProductServiceIntegrationTest {

  @Autowired private CategoryRepository categoryRepository;
  @Autowired private InventoryRepository inventoryRepository;
  @Autowired private ProductCatalogService productCatalogService;
  @Autowired private InventoryManagerService inventoryManagerService;

  @Test
  void startup_seedsDefaultCategories() {
    assertThat(categoryRepository.findAll())
        .extracting("id")
        .contains("ELECTRONICS", "BOOKS", "CLOTHING", "HOME", "SPORTS");
  }

  @Test
  void createThenReserve_persistsStockAndBumpsVersion() {
    productCatalogService.createProduct(
        new ProductCreationDTO("IT-1", "Laptop", "d", new BigDecimal("999.00"), "electronics", 3, null));
    Long initialVersion = inventoryRepository.findById("IT-1").orElseThrow().getVersion();

    inventoryManagerService.reserveStock(List.of(new InventoryReservationRequest("O1", "IT-1", 2)));

    var inventory = inventoryRepository.findById("IT-1").orElseThrow();
    assertThat(inventory.getReservedStock()).isEqualTo(2);
    assertThat(inventory.getVersion()).isGreaterThan(initialVersion);
    assertThat(productCatalogService.getProductDetails("IT-1").getReservedStock()).isEqualTo(2);
    assertThatThrownBy(
            () ->
                inventoryManagerService.reserveStock(
                    List.of(new InventoryReservationRequest("O2", "IT-1", 2))))
        .isInstanceOf(ApplicationBaseException.class)
        .extracting("errorCode")
        .isEqualTo("PROD_4002");
  }
}
