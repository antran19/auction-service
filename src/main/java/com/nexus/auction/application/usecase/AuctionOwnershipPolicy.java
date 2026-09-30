package com.nexus.auction.application.usecase;

import com.nexus.auction.domain.model.Auction;
import com.nexus.common.core.exception.ForbiddenException;

final class AuctionOwnershipPolicy {

    private AuctionOwnershipPolicy() {
    }

    static void requireOwner(Auction auction, String callerId) {
        if (!auction.getSellerId().equals(callerId)) {
            throw new ForbiddenException("AUCTION_NOT_OWNED",
                    "You can only modify your own auctions: " + auction.getId());
        }
    }
}
