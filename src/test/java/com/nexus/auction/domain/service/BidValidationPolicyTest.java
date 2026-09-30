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

    private Auction activeAuctionNoBidsYet() {
        return Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                        Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.HOURS))
                .withStatus(AuctionStatus.ACTIVE);
    }

    @Test
    void validate_acceptsBidEqualToStartingPriceWhenNoBidsYet() {
        Auction auction = activeAuctionNoBidsYet();

        assertThatCode(() -> BidValidationPolicy.validate(auction, new BigDecimal("100.00")))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_rejectsBidBelowStartingPriceWhenNoBidsYet() {
        Auction auction = activeAuctionNoBidsYet();

        assertThatThrownBy(() -> BidValidationPolicy.validate(auction, new BigDecimal("99.99")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_acceptsBidExactlyAtHighestPlusIncrement() {
        Auction auction = activeAuctionNoBidsYet().withBid("bidder-1", new BigDecimal("100.00"));

        // 100.00 (current highest) + 10.00 (increment) = 110.00 — must be accepted, not rejected
        // for being "not strictly greater". This is the Review Focus off-by-one case.
        assertThatCode(() -> BidValidationPolicy.validate(auction, new BigDecimal("110.00")))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_rejectsBidBelowHighestPlusIncrement() {
        Auction auction = activeAuctionNoBidsYet().withBid("bidder-1", new BigDecimal("100.00"));

        assertThatThrownBy(() -> BidValidationPolicy.validate(auction, new BigDecimal("109.99")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_rejectsBidOnPendingAuction() {
        Auction pending = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                Instant.now().plus(1, ChronoUnit.HOURS), Instant.now().plus(2, ChronoUnit.HOURS));

        assertThatThrownBy(() -> BidValidationPolicy.validate(pending, new BigDecimal("100.00")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void validate_rejectsBidOnEndedAuction() {
        Auction ended = activeAuctionNoBidsYet().withStatus(AuctionStatus.ENDED);

        assertThatThrownBy(() -> BidValidationPolicy.validate(ended, new BigDecimal("100.00")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void validate_rejectsBidOnCancelledAuction() {
        Auction cancelled = activeAuctionNoBidsYet().withStatus(AuctionStatus.CANCELLED);

        assertThatThrownBy(() -> BidValidationPolicy.validate(cancelled, new BigDecimal("100.00")))
                .isInstanceOf(ConflictException.class);
    }
}
