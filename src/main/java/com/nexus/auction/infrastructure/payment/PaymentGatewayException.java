package com.nexus.auction.infrastructure.payment;

// Deliberately a plain RuntimeException, not a DomainException subclass: a Stripe outage or
// misconfiguration is an infrastructure failure, not a business-rule violation, so it should
// surface as a 500 via GlobalExceptionHandler's generic handler, not a mapped domain error.
public class PaymentGatewayException extends RuntimeException {
    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
