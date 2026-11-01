package com.nexus.auction.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AutoBidTest {

    @Test
    void create_setsBothTimestampsToTheSameInstant() {
        AutoBid autoBid = AutoBid.create("auction-1", "bidder-1", new BigDecimal("500.00"));

        assertThat(autoBid.getId()).isNotBlank();
        assertThat(autoBid.getMaxAmount()).isEqualByComparingTo("500.00");
        assertThat(autoBid.getUpdatedAt()).isEqualTo(autoBid.getCreatedAt());
    }

    @Test
    void withMaxAmount_keepsIdentityAndCreatedAt_soTheTieBreakOrderSurvives() throws InterruptedException {
        AutoBid first = AutoBid.create("auction-1", "bidder-1", new BigDecimal("500.00"));
        Thread.sleep(5);

        AutoBid raised = first.withMaxAmount(new BigDecimal("800.00"));

        assertThat(raised.getId()).isEqualTo(first.getId());
        assertThat(raised.getAuctionId()).isEqualTo("auction-1");
        assertThat(raised.getBidderId()).isEqualTo("bidder-1");
        assertThat(raised.getCreatedAt()).isEqualTo(first.getCreatedAt());
        assertThat(raised.getUpdatedAt()).isAfter(first.getUpdatedAt());
        assertThat(raised.getMaxAmount()).isEqualByComparingTo("800.00");
        assertThat(first.getMaxAmount()).isEqualByComparingTo("500.00");
    }
}
