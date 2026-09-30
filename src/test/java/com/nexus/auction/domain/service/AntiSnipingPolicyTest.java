package com.nexus.auction.domain.service;

import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AntiSnipingPolicyTest {

    @Test
    void tryExtend_extendsWhenBidArrivesWithinFiveMinutesOfEnd() {
        Instant now = Instant.now();
        Auction auction = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                        now.minus(1, ChronoUnit.HOURS), now.plus(3, ChronoUnit.MINUTES))
                .withStatus(AuctionStatus.ACTIVE);

        Optional<Instant> extended = AntiSnipingPolicy.tryExtend(auction, now);

        assertThat(extended).isPresent();
        assertThat(extended.get()).isEqualTo(auction.getEndTime().plus(5, ChronoUnit.MINUTES));
    }

    @Test
    void tryExtend_doesNotExtendWhenBidArrivesOutsideTheWindow() {
        Instant now = Instant.now();
        Auction auction = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                        now.minus(1, ChronoUnit.HOURS), now.plus(30, ChronoUnit.MINUTES))
                .withStatus(AuctionStatus.ACTIVE);

        Optional<Instant> extended = AntiSnipingPolicy.tryExtend(auction, now);

        assertThat(extended).isEmpty();
    }

    @Test
    void tryExtend_stopsExtendingOnceMaxExtensionsReached() {
        Instant now = Instant.now();
        Auction auction = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                        now.minus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.MINUTES))
                .withStatus(AuctionStatus.ACTIVE);
        // Simulate 12 prior extensions (MAX_AUCTION_EXTENSIONS) via repeated withExtendedEndTime.
        for (int i = 0; i < 12; i++) {
            auction = auction.withExtendedEndTime(auction.getEndTime().plus(5, ChronoUnit.MINUTES));
        }
        assertThat(auction.getExtensionCount()).isEqualTo(12);
        // Bid arrives inside the anti-sniping window of the CURRENT (already-extended) end time —
        // isolates the cap check from the window check, rather than reusing a stale `now`.
        Instant bidArrivesAt = auction.getEndTime().minus(3, ChronoUnit.MINUTES);

        Optional<Instant> extended = AntiSnipingPolicy.tryExtend(auction, bidArrivesAt);

        assertThat(extended).isEmpty();
    }

    @Test
    void tryExtend_allowsTheTwelfthExtensionExactlyAtTheCap() {
        Instant now = Instant.now();
        Auction auction = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                        now.minus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.MINUTES))
                .withStatus(AuctionStatus.ACTIVE);
        for (int i = 0; i < 11; i++) {
            auction = auction.withExtendedEndTime(auction.getEndTime().plus(5, ChronoUnit.MINUTES));
        }
        assertThat(auction.getExtensionCount()).isEqualTo(11);
        Instant bidArrivesAt = auction.getEndTime().minus(3, ChronoUnit.MINUTES);

        Optional<Instant> extended = AntiSnipingPolicy.tryExtend(auction, bidArrivesAt);

        assertThat(extended).isPresent();
    }
}
