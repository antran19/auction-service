package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AutoBidRepositoryPort;
import com.nexus.auction.application.port.out.BidBroadcastPort;
import com.nexus.auction.application.port.out.BidRepositoryPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AutoBid;
import com.nexus.auction.domain.model.Bid;
import com.nexus.auction.domain.service.AutoBidResolver;

import java.util.List;

// Places the bids the standing maximums owe after something changed on an auction. Must be called
// inside the transaction that holds the row lock of the auction. It stores and announces the
// bids and returns the auction with its new highest bid; saving the auction, the extension of
// its end time and the notifications stay with the caller. When nothing is owed the very same
// auction object is returned.
public class AutoBidResponder {

    private final BidRepositoryPort bidRepositoryPort;
    private final AutoBidRepositoryPort autoBidRepositoryPort;
    private final BidBroadcastPort bidBroadcastPort;

    public AutoBidResponder(BidRepositoryPort bidRepositoryPort, AutoBidRepositoryPort autoBidRepositoryPort,
                            BidBroadcastPort bidBroadcastPort) {
        this.bidRepositoryPort = bidRepositoryPort;
        this.autoBidRepositoryPort = autoBidRepositoryPort;
        this.bidBroadcastPort = bidBroadcastPort;
    }

    public Auction answer(Auction auction) {
        List<AutoBid> autoBids = autoBidRepositoryPort.findByAuctionId(auction.getId());
        List<AutoBidResolver.Step> steps = AutoBidResolver.resolve(auction.getStartingPrice(), auction.getBidIncrement(),
                auction.getCurrentHighestBid(), auction.getCurrentHighestBidderId(), autoBids);
        Auction current = auction;
        for (AutoBidResolver.Step step : steps) {
            Bid bid = Bid.create(auction.getId(), step.bidderId(), step.amount());
            bidRepositoryPort.save(bid);
            current = current.withBid(step.bidderId(), step.amount());
            bidBroadcastPort.bidPlaced(new BidResult(bid.getId(), auction.getId(), step.bidderId(),
                    step.amount(), bid.getPlacedAt()));
        }
        return current;
    }
}
