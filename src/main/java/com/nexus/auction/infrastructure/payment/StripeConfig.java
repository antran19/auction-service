package com.nexus.auction.infrastructure.payment;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    @Value("${nexus.payment.stripe.secret-key}")
    private String secretKey;

    // Stripe's SDK is configured through this one static field, not per-instance -- there is
    // no per-request client object to construct, so setting it once at startup is correct.
    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }
}
