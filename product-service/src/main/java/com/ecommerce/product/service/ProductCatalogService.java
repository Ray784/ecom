package com.ecommerce.product.service;

import com.ecommerce.common.dto.ProductCreationDTO;
import com.ecommerce.common.dto.ProductDetailsDTO;
import com.ecommerce.common.dto.ProductSummaryDTO;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.product.model.Inventory;
import com.ecommerce.product.model.Product;
import com.ecommerce.product.repository.InventoryRepository;
import com.ecommerce.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Manages the product catalog and keeps search-service in sync. */
@Service
public class ProductCatalogService {

  /** Maximum allowed length of a product name. */
  public static final int MAX_NAME_LENGTH = 255;

  /** Maximum allowed length of a product description. */
  public static final int MAX_DESCRIPTION_LENGTH = 2000;

  private final ProductRepository productRepository;
  private final InventoryRepository inventoryRepository;
  private final CategoryService categoryService;
  private final SearchIndexClient searchIndexClient;
  private final TransactionTemplate transactionTemplate;

  public ProductCatalogService(
      ProductRepository productRepository,
      InventoryRepository inventoryRepository,
      CategoryService categoryService,
      SearchIndexClient searchIndexClient,
      TransactionTemplate transactionTemplate) {
    this.productRepository = productRepository;
    this.inventoryRepository = inventoryRepository;
    this.categoryService = categoryService;
    this.searchIndexClient = searchIndexClient;
    this.transactionTemplate = transactionTemplate;
  }

  /** Returns catalog data merged with stock counters, or PROD_4001 when the SKU is unknown. */
  public ProductDetailsDTO getProductDetails(String sku) {
    Product product = findProduct(sku);
    return toDetails(product, inventoryRepository.findById(product.getSku()).orElse(null));
  }

  /**
   * Creates a product with its inventory row and pushes it to search-service.
   *
   * <p>A blank SKU is replaced by a generated one. A missing initial stock defaults to zero.
   */
  public ProductDetailsDTO createProduct(ProductCreationDTO request) {
    validate(request);
    String sku =
        isBlank(request.getSku())
            ? "SKU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT)
            : request.getSku().trim();
    int initialStock = request.getInitialStock() == null ? 0 : request.getInitialStock();
    String categoryId = CategoryService.normalizeId(request.getCategoryId());

    ProductDetailsDTO created =
        transactionTemplate.execute(
            status -> {
              if (productRepository.existsById(sku)) {
                throw CodedErrorFactory.create(MappedError.PROD_4004, sku);
              }
              Product product =
                  productRepository.save(
                      new Product(
                          sku,
                          request.getName().trim(),
                          request.getDescription(),
                          request.getPrice(),
                          categoryId,
                          request.getImageUrl(),
                          null));
              Inventory inventory =
                  inventoryRepository.save(new Inventory(sku, initialStock, 0));
              return toDetails(product, inventory);
            });
    searchIndexClient.pushProduct(toSummary(created, request.getImageUrl()));
    created.setStatusCode(201);
    created.setMessage("Product " + sku + " created");
    return created;
  }

  /**
   * Replaces the editable fields of an existing product.
   *
   * <p>When initialStock is present it overwrites currentStock but may not drop below the units
   * already reserved.
   */
  public ProductDetailsDTO updateProduct(String sku, ProductCreationDTO request) {
    validate(request);
    String categoryId = CategoryService.normalizeId(request.getCategoryId());
    ProductDetailsDTO updated =
        transactionTemplate.execute(
            status -> {
              Product product = findProduct(sku);
              product.setName(request.getName().trim());
              product.setDescription(request.getDescription());
              product.setPrice(request.getPrice());
              product.setCategoryId(categoryId);
              product.setImageUrl(request.getImageUrl());
              Inventory inventory =
                  inventoryRepository
                      .findById(product.getSku())
                      .orElseGet(() -> new Inventory(product.getSku(), 0, 0));
              if (request.getInitialStock() != null) {
                if (request.getInitialStock() < inventory.getReservedStock()) {
                  throw CodedErrorFactory.create(MappedError.PROD_4007);
                }
                inventory.setCurrentStock(request.getInitialStock());
              }
              return toDetails(productRepository.save(product), inventoryRepository.save(inventory));
            });
    searchIndexClient.pushProduct(toSummary(updated, request.getImageUrl()));
    updated.setMessage("Product " + updated.getSku() + " updated");
    return updated;
  }

  public List<ProductDetailsDTO> listAllProducts() {
    return productRepository.findAll().stream()
        .map(p -> toDetails(p, inventoryRepository.findById(p.getSku()).orElse(null)))
        .toList();
  }

  private Product findProduct(String sku) {
    if (isBlank(sku)) {
      throw CodedErrorFactory.create(MappedError.PROD_4001, sku);
    }
    return productRepository
        .findById(sku.trim())
        .orElseThrow(() -> CodedErrorFactory.create(MappedError.PROD_4001, sku));
  }

  private void validate(ProductCreationDTO request) {
    if (request == null || isBlank(request.getName())) {
      throw CodedErrorFactory.create(MappedError.PROD_4010, MAX_NAME_LENGTH);
    }
    if (request.getName().trim().length() > MAX_NAME_LENGTH) {
      throw CodedErrorFactory.create(MappedError.PROD_4010, MAX_NAME_LENGTH);
    }
    if (request.getDescription() != null
        && request.getDescription().length() > MAX_DESCRIPTION_LENGTH) {
      throw CodedErrorFactory.create(MappedError.PROD_4010, MAX_DESCRIPTION_LENGTH);
    }
    if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
      throw CodedErrorFactory.create(MappedError.PROD_4005);
    }
    if (request.getInitialStock() != null && request.getInitialStock() < 0) {
      throw CodedErrorFactory.create(MappedError.PROD_4007);
    }
    if (!categoryService.exists(request.getCategoryId())) {
      throw CodedErrorFactory.create(MappedError.PROD_4003, request.getCategoryId());
    }
  }

  static ProductDetailsDTO toDetails(Product product, Inventory inventory) {
    ProductDetailsDTO dto =
        new ProductDetailsDTO(
            product.getSku(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getCategoryId(),
            inventory == null ? 0 : inventory.getCurrentStock(),
            inventory == null ? 0 : inventory.getReservedStock(),
            product.getAverageRating());
    dto.setSuccess(true);
    dto.setStatusCode(200);
    dto.setMessage("OK");
    return dto;
  }

  private static ProductSummaryDTO toSummary(ProductDetailsDTO details, String imageUrl) {
    return new ProductSummaryDTO(
        details.getSku(),
        details.getName(),
        details.getDescription(),
        details.getPrice(),
        details.getCategoryId(),
        imageUrl);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
