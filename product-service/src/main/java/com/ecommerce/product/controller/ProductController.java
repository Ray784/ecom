package com.ecommerce.product.controller;

import com.ecommerce.common.dto.ProductCreationDTO;
import com.ecommerce.common.dto.ProductDetailsDTO;
import com.ecommerce.product.service.ProductCatalogService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for the product catalog. */
@RestController
@RequestMapping("/products")
public class ProductController {

  private final ProductCatalogService productCatalogService;

  public ProductController(ProductCatalogService productCatalogService) {
    this.productCatalogService = productCatalogService;
  }

  @PostMapping
  public ResponseEntity<ProductDetailsDTO> createProduct(@RequestBody ProductCreationDTO request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(productCatalogService.createProduct(request));
  }

  @GetMapping("/{id}")
  public ProductDetailsDTO getProduct(@PathVariable("id") String id) {
    return productCatalogService.getProductDetails(id);
  }

  @PutMapping("/{id}")
  public ProductDetailsDTO updateProduct(
      @PathVariable("id") String id, @RequestBody ProductCreationDTO request) {
    return productCatalogService.updateProduct(id, request);
  }

  @GetMapping
  public List<ProductDetailsDTO> listProducts() {
    return productCatalogService.listAllProducts();
  }
}
