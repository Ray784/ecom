package com.ecommerce.payment.gateway;

/**
 * Outcome reported by the payment gateway for one charge request.
 *
 * @param approved true when the gateway captured the funds
 * @param gatewayCode gateway specific result code, APPROVED on success
 * @param gatewayRef Razorpay pay_ ID assigned to the charge, null when no payment was created
 * @param razorpayOrderId Razorpay order_ ID the charge was attempted on, null when none was created
 */
public record PaymentGatewayResponse(
    boolean approved, String gatewayCode, String gatewayRef, String razorpayOrderId) {}
