package com.nexus.auction.application.port.out;

import com.nexus.auction.domain.model.Bid;

import java.util.List;

public interface BidRepositoryPort {
    Bid save(Bid bid);
    List<Bid> findByAuctionId(String auctionId, int page, int size);

    // The auctions this person has bid on, most recently active first. Bids the system placed for
    // their maximum count too: they are bids under their name.
    List<BidderAuctionSummary> findAuctionsBidOnBy(String bidderId, int page, int size);

    // One entry per auction that has at least one bid; auctions without bids are simply absent.
    List<AuctionBidStats> statsByAuctionIds(List<String> auctionIds);
}
