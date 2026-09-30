package com.nexus.auction.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AuctionTest {

    @Test
    void create_startsInPendingWithNoBidsAndZeroExtensions() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        Auction auction = Auction.create("product-1", "seller-1", new BigDecimal("100.00"),
                new BigDecimal("10.00"), start, end);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.PENDING);
        assertThat(auction.getCurrentHighestBid()).isNull();
        assertThat(auction.getCurrentHighestBidderId()).isNull();
        assertThat(auction.getExtensionCount()).isZero();
        assertThat(auction.getWinnerId()).isNull();
        assertThat(auction.getId()).isNotBlank();
    }

    @Test
    void withStatus_returnsNewInstanceWithUpdatedStatus() {
        Auction pending = Auction.create("product-1", "seller-1", new BigDecimal("100.00"),
                new BigDecimal("10.00"), Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));

        Auction active = pending.withStatus(AuctionStatus.ACTIVE);

        assertThat(active.getStatus()).isEqualTo(AuctionStatus.ACTIVE);
        assertThat(pending.getStatus()).isEqualTo(AuctionStatus.PENDING);
        assertThat(active.getId()).isEqualTo(pending.getId());
    }

    @Test
    void withBid_updatesHighestBidAndBidder() {
        Auction auction = Auction.create("product-1", "seller-1", new BigDecimal("100.00"),
                new BigDecimal("10.00"), Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));

        Auction bidded = auction.withBid("bidder-1", new BigDecimal("100.00"));

        assertThat(bidded.getCurrentHighestBid()).isEqualByComparingTo("100.00");
        assertThat(bidded.getCurrentHighestBidderId()).isEqualTo("bidder-1");
    }

    @Test
    void withExtendedEndTime_pushesEndTimeAndIncrementsExtensionCount() {
        Instant originalEnd = Instant.now().plus(1, ChronoUnit.HOURS);
        Auction auction = Auction.create("product-1", "seller-1", new BigDecimal("100.00"),
                new BigDecimal("10.00"), Instant.now(), originalEnd);
        Instant newEnd = originalEnd.plus(5, ChronoUnit.MINUTES);

        Auction extended = auction.withExtendedEndTime(newEnd);

        assertThat(extended.getEndTime()).isEqualTo(newEnd);
        assertThat(extended.getExtensionCount()).isEqualTo(1);
    }

    @Test
    void withSettlement_setsWinnerFinalPriceAndEndedStatus() {
        Auction auction = Auction.create("product-1", "seller-1", new BigDecimal("100.00"),
                new BigDecimal("10.00"), Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));
        Instant deadline = Instant.now().plus(24, ChronoUnit.HOURS);

        Auction settled = auction.withSettlement("bidder-1", new BigDecimal("150.00"), deadline);

        assertThat(settled.getStatus()).isEqualTo(AuctionStatus.ENDED);
        assertThat(settled.getWinnerId()).isEqualTo("bidder-1");
        assertThat(settled.getFinalPrice()).isEqualByComparingTo("150.00");
        assertThat(settled.getPaymentDeadline()).isEqualTo(deadline);
    }

    @Test
    void withPaymentTimeoutEmitted_setsFlag() {
        Auction auction = Auction.create("product-1", "seller-1", new BigDecimal("100.00"),
                new BigDecimal("10.00"), Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));

        Auction flagged = auction.withPaymentTimeoutEmitted();

        assertThat(flagged.isPaymentTimeoutEmitted()).isTrue();
        assertThat(auction.isPaymentTimeoutEmitted()).isFalse();
    }
}
