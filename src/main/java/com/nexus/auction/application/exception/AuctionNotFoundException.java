package com.nexus.auction.application.exception;

import com.nexus.common.core.exception.NotFoundException;

public class AuctionNotFoundException extends NotFoundException {
    public AuctionNotFoundException(String id) {
        super("AUCTION_NOT_FOUND", "Auction not found: " + id);
    }
}
