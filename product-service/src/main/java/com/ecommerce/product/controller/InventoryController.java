package com.ecommerce.product.controller;

import com.ecommerce.common.dto.InventoryReservationRequest;
import com.ecommerce.common.dto.InventoryReservedEvent;
import com.ecommerce.product.service.InventoryManagerService;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Synchronous inventory reservation endpoint. Errors surface as PROD catalog codes. */
@RestController
@RequestMapping("/inventory")
public class InventoryController {

  private final InventoryManagerService inventoryManagerService;

  public InventoryController(InventoryManagerService inventoryManagerService) {
    this.inventoryManagerService = inventoryManagerService;
  }

  @PostMapping("/reserve")
  public InventoryReservedEvent reserve(@RequestBody InventoryReservationRequest request) {
    inventoryManagerService.reserveStock(List.of(request));
    return new InventoryReservedEvent(
        request.getOrderId(), true, "Reserved " + request.getItems().size() + " item lines");
  }
}
