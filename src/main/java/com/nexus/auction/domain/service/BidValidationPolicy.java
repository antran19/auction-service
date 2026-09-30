package com.nexus.auction.domain.service;

import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import com.nexus.common.core.FieldError;
import com.nexus.common.core.exception.ConflictException;
import com.nexus.common.core.exception.ValidationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class BidValidationPolicy {

    private BidValidationPolicy() {
    }

    public static void validate(Auction auction, String bidderId, BigDecimal amount, Instant now) {
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new ConflictException("AUCTION_NOT_ACTIVE",
                    "Cannot bid on an auction that is not active: " + auction.getId());
        }
        // Closes the gap between the auction's actual end_time passing and the next
        // AuctionLifecycleJob poll tick (up to 10s, longer under a Kafka outage) — without
        // this, status is still ACTIVE and a bid placed in that gap would otherwise be
        // accepted, including re-triggering an anti-sniping extension that reopens a
        // closed auction.
        if (!now.isBefore(auction.getEndTime())) {
            throw new ConflictException("AUCTION_ENDED",
                    "Cannot bid on an auction whose end time has passed: " + auction.getId());
        }
        if (bidderId.equals(auction.getSellerId())) {
            throw new ConflictException("SELLER_CANNOT_BID_OWN_AUCTION",
                    "A seller cannot bid on their own auction: " + auction.getId());
        }

        BigDecimal minimum = auction.getCurrentHighestBid() == null
                ? auction.getStartingPrice()
                : auction.getCurrentHighestBid().add(auction.getBidIncrement());

        if (amount.compareTo(minimum) < 0) {
            throw new ValidationException(List.of(new FieldError("amount",
                    "Bid must be at least " + minimum)));
        }
    }
}
