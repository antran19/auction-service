package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.BidRepositoryPort;
import com.nexus.auction.domain.model.Bid;

import java.util.List;

public class GetBidHistoryUseCase {

    private final BidRepositoryPort bidRepositoryPort;

    public GetBidHistoryUseCase(BidRepositoryPort bidRepositoryPort) {
        this.bidRepositoryPort = bidRepositoryPort;
    }

    public List<BidResult> getHistory(String auctionId, int page, int size) {
        return bidRepositoryPort.findByAuctionId(auctionId, page, size).stream()
                .map(this::toResult).toList();
    }

    private BidResult toResult(Bid bid) {
        return new BidResult(bid.getId(), bid.getAuctionId(), bid.getBidderId(), bid.getAmount(), bid.getPlacedAt());
    }
}
