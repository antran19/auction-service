package com.nexus.auction.application.port.out;

import com.nexus.auction.domain.model.AutoBid;

import java.util.List;
import java.util.Optional;

public interface AutoBidRepositoryPort {
    AutoBid save(AutoBid autoBid);
    Optional<AutoBid> findByAuctionIdAndBidderId(String auctionId, String bidderId);
    // Oldest first: that is the order that decides a tie between equal maximums.
    List<AutoBid> findByAuctionId(String auctionId);

    // This person's maximums on the given auctions (at most one per auction).
    List<AutoBid> findByBidderIdAndAuctionIds(String bidderId, List<String> auctionIds);
}
