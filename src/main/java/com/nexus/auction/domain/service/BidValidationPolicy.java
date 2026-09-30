package com.nexus.auction.domain.service;

import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import com.nexus.common.core.FieldError;
import com.nexus.common.core.exception.ConflictException;
import com.nexus.common.core.exception.ValidationException;

import java.math.BigDecimal;
import java.util.List;

public final class BidValidationPolicy {

    private BidValidationPolicy() {
    }

    public static void validate(Auction auction, BigDecimal amount) {
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new ConflictException("AUCTION_NOT_ACTIVE",
                    "Cannot bid on an auction that is not active: " + auction.getId());
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
