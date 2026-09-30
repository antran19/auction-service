package com.nexus.auction.application.port.out;

import com.nexus.auction.domain.model.Bid;

import java.util.List;

public interface BidRepositoryPort {
    Bid save(Bid bid);
    List<Bid> findByAuctionId(String auctionId, int page, int size);
}
