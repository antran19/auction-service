package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;

import java.util.List;

public class ListAuctionsUseCase {

    private final AuctionRepositoryPort auctionRepositoryPort;

    public ListAuctionsUseCase(AuctionRepositoryPort auctionRepositoryPort) {
        this.auctionRepositoryPort = auctionRepositoryPort;
    }

    public List<AuctionResult> list(AuctionSearchQuery query) {
        return auctionRepositoryPort.search(query.status(), query.sellerId(), query.productId(),
                        query.page(), query.size())
                .stream().map(CreateAuctionUseCase::toResult).toList();
    }
}
