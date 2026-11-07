package com.nexus.auction.application.usecase;

import com.nexus.auction.application.exception.AuctionNotFoundException;
import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.AutoBidRepositoryPort;
import com.nexus.auction.domain.model.AutoBid;
import com.nexus.common.core.exception.NotFoundException;

// A person reads their own maximum on one auction. The maximum is hidden from everyone else, so
// there is no way to ask for another person's: the bidder always comes from the token.
public class GetMyAutoBidUseCase {

    private final AuctionRepositoryPort auctionRepositoryPort;
    private final AutoBidRepositoryPort autoBidRepositoryPort;

    public GetMyAutoBidUseCase(AuctionRepositoryPort auctionRepositoryPort, AutoBidRepositoryPort autoBidRepositoryPort) {
        this.auctionRepositoryPort = auctionRepositoryPort;
        this.autoBidRepositoryPort = autoBidRepositoryPort;
    }

    public AutoBidResult get(String auctionId, String bidderId) {
        if (auctionRepositoryPort.findById(auctionId).isEmpty()) {
            throw new AuctionNotFoundException(auctionId);
        }
        AutoBid autoBid = autoBidRepositoryPort.findByAuctionIdAndBidderId(auctionId, bidderId)
                .orElseThrow(() -> new NotFoundException("AUTO_BID_NOT_FOUND",
                        "You have not set a maximum on this auction: " + auctionId));
        return new AutoBidResult(autoBid.getId(), autoBid.getAuctionId(), autoBid.getBidderId(),
                autoBid.getMaxAmount(), autoBid.getUpdatedAt());
    }
}
