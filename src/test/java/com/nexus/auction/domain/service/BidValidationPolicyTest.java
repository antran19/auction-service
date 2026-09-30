package com.nexus.auction.domain.service;

import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import com.nexus.common.core.exception.ConflictException;
import com.nexus.common.core.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BidValidationPolicyTest {

    private static final String BIDDER_ID = "bidder-1";

    private Auction activeAuctionNoBidsYet() {
        return Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                        Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.HOURS))
                .withStatus(AuctionStatus.ACTIVE);
    }

    @Test
    void validate_acceptsBidEqualToStartingPriceWhenNoBidsYet() {
        Auction auction = activeAuctionNoBidsYet();

        assertThatCode(() -> BidValidationPolicy.validate(auction, BIDDER_ID, new BigDecimal("100.00"), Instant.now()))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_rejectsBidBelowStartingPriceWhenNoBidsYet() {
        Auction auction = activeAuctionNoBidsYet();

        assertThatThrownBy(() -> BidValidationPolicy.validate(auction, BIDDER_ID, new BigDecimal("99.99"), Instant.now()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_acceptsBidExactlyAtHighestPlusIncrement() {
        Auction auction = activeAuctionNoBidsYet().withBid("bidder-2", new BigDecimal("100.00"));

        // 100.00 (current highest) + 10.00 (increment) = 110.00 — must be accepted, not rejected
        // for being "not strictly greater". This is the Review Focus off-by-one case.
        assertThatCode(() -> BidValidationPolicy.validate(auction, BIDDER_ID, new BigDecimal("110.00"), Instant.now()))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_rejectsBidBelowHighestPlusIncrement() {
        Auction auction = activeAuctionNoBidsYet().withBid("bidder-2", new BigDecimal("100.00"));

        assertThatThrownBy(() -> BidValidationPolicy.validate(auction, BIDDER_ID, new BigDecimal("109.99"), Instant.now()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_rejectsBidOnPendingAuction() {
        Auction pending = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                Instant.now().plus(1, ChronoUnit.HOURS), Instant.now().plus(2, ChronoUnit.HOURS));

        assertThatThrownBy(() -> BidValidationPolicy.validate(pending, BIDDER_ID, new BigDecimal("100.00"), Instant.now()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void validate_rejectsBidOnEndedAuction() {
        Auction ended = activeAuctionNoBidsYet().withStatus(AuctionStatus.ENDED);

        assertThatThrownBy(() -> BidValidationPolicy.validate(ended, BIDDER_ID, new BigDecimal("100.00"), Instant.now()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void validate_rejectsBidOnCancelledAuction() {
        Auction cancelled = activeAuctionNoBidsYet().withStatus(AuctionStatus.CANCELLED);

        assertThatThrownBy(() -> BidValidationPolicy.validate(cancelled, BIDDER_ID, new BigDecimal("100.00"), Instant.now()))
                .isInstanceOf(ConflictException.class);
    }

    // --- Fix-pass additions (2026-09-30 final review) ---

    @Test
    void validate_rejectsBidWhoseEndTimeHasAlreadyPassedEvenIfStatusIsStillActive() {
        // Simulates the real gap: AuctionLifecycleJob only ticks every 10s (or much longer
        // under a Kafka outage), so `status` can still read ACTIVE for a while after
        // `end_time` has actually passed. The bid must be rejected on end_time alone, not
        // wait for the job to catch up and flip status to ENDED.
        Instant now = Instant.now();
        Auction auction = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                        now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.MINUTES))
                .withStatus(AuctionStatus.ACTIVE);

        assertThatThrownBy(() -> BidValidationPolicy.validate(auction, BIDDER_ID, new BigDecimal("100.00"), now))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(auction.getId());
    }

    @Test
    void validate_rejectsBidAtExactlyTheEndTime() {
        Instant now = Instant.now();
        Auction auction = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                        now.minus(1, ChronoUnit.HOURS), now)
                .withStatus(AuctionStatus.ACTIVE);

        assertThatThrownBy(() -> BidValidationPolicy.validate(auction, BIDDER_ID, new BigDecimal("100.00"), now))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void validate_rejectsBidFromTheAuctionsOwnSeller() {
        Auction auction = activeAuctionNoBidsYet();

        assertThatThrownBy(() -> BidValidationPolicy.validate(auction, "seller-1", new BigDecimal("100.00"), Instant.now()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(auction.getId());
    }
}
