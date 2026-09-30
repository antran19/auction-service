package com.nexus.auction.application.usecase;

import com.nexus.auction.application.exception.AuctionNotFoundException;
import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.domain.model.Auction;

public class GetAuctionUseCase {

    private final AuctionRepositoryPort auctionRepositoryPort;

    public GetAuctionUseCase(AuctionRepositoryPort auctionRepositoryPort) {
        this.auctionRepositoryPort = auctionRepositoryPort;
    }

    public AuctionResult get(String id) {
        Auction auction = auctionRepositoryPort.findById(id).orElseThrow(() -> new AuctionNotFoundException(id));
        return CreateAuctionUseCase.toResult(auction);
    }
}
