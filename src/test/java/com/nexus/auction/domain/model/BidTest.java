package com.nexus.auction.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BidTest {

    @Test
    void create_generatesIdAndPlacedAtTimestamp() {
        Bid bid = Bid.create("auction-1", "bidder-1", new BigDecimal("120.00"));

        assertThat(bid.getId()).isNotBlank();
        assertThat(bid.getAuctionId()).isEqualTo("auction-1");
        assertThat(bid.getBidderId()).isEqualTo("bidder-1");
        assertThat(bid.getAmount()).isEqualByComparingTo("120.00");
        assertThat(bid.getPlacedAt()).isNotNull();
    }
}
