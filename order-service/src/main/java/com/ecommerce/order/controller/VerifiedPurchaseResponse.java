package com.ecommerce.order.controller;

/** Body of GET /orders/verified-purchase telling whether the user bought the SKU and paid. */
public record VerifiedPurchaseResponse(String userId, String sku, boolean verified) {}
