package com.nexus.auction.infrastructure.payment;

import com.nexus.auction.application.port.out.PaymentGatewayPort;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class StripePaymentGatewayAdapter implements PaymentGatewayPort {

    // VND is one of Stripe's zero-decimal currencies -- unlike USD, unit_amount is the whole
    // VND amount itself, NOT the amount * 100. Multiplying here would overcharge 100x.
    private static final String CURRENCY = "vnd";

    @Override
    public CheckoutSession createCheckoutSession(String auctionId, String winnerId, BigDecimal amount,
                                                  String successUrl, String cancelUrl) {
        long unitAmount = amount.setScale(0, RoundingMode.HALF_UP).longValueExact();

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl + (successUrl.contains("?") ? "&" : "?") + "session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(cancelUrl)
                .putMetadata("auctionId", auctionId)
                .putMetadata("winnerId", winnerId)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(CURRENCY)
                                .setUnitAmount(unitAmount)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Nexus — Auction " + auctionId)
                                        .build())
                                .build())
                        .build())
                .build();

        try {
            Session session = Session.create(params);
            return new CheckoutSession(session.getId(), session.getUrl());
        } catch (StripeException e) {
            // Stripe's own exception is a checked exception with no domain meaning here; wrap
            // it so a Stripe outage/misconfiguration surfaces as a normal 500, not an unmapped
            // checked-exception compile error one layer up.
            throw new PaymentGatewayException("Failed to create Stripe checkout session for auction " + auctionId, e);
        }
    }

    @Override
    public PaymentStatus retrieveSessionStatus(String sessionId) {
        try {
            Session session = Session.retrieve(sessionId);
            boolean paid = "paid".equals(session.getPaymentStatus());
            String auctionId = session.getMetadata() == null ? null : session.getMetadata().get("auctionId");
            String winnerId = session.getMetadata() == null ? null : session.getMetadata().get("winnerId");
            return new PaymentStatus(paid, auctionId, winnerId);
        } catch (StripeException e) {
            throw new PaymentGatewayException("Failed to retrieve Stripe checkout session " + sessionId, e);
        }
    }
}
