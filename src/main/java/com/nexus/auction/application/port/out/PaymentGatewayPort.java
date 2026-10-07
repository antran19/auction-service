package com.nexus.auction.application.port.out;

import java.math.BigDecimal;

public interface PaymentGatewayPort {

    CheckoutSession createCheckoutSession(String auctionId, String winnerId, BigDecimal amount,
                                           String successUrl, String cancelUrl);

    PaymentStatus retrieveSessionStatus(String sessionId);

    record CheckoutSession(String sessionId, String checkoutUrl) {
    }

    record PaymentStatus(boolean paid, String auctionId, String winnerId) {
    }
}
