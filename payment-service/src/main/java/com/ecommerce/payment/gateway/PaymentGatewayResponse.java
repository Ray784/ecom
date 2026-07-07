package com.ecommerce.payment.gateway;

/**
 * Outcome reported by the payment gateway for one charge request.
 *
 * @param approved true when the gateway captured the funds
 * @param gatewayCode gateway specific result code, APPROVED on success
 * @param gatewayRef reference assigned by the gateway to the charge
 */
public record PaymentGatewayResponse(boolean approved, String gatewayCode, String gatewayRef) {}
