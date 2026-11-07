package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.AutoBidRepositoryPort;
import com.nexus.auction.application.port.out.BidRepositoryPort;
import com.nexus.auction.application.port.out.BidderAuctionSummary;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AutoBid;
import com.nexus.common.core.FieldError;
import com.nexus.common.core.exception.ValidationException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// "What am I bidding on?": the auctions this person has bid on, the ones they touched most
// recently first, with where they stand in each. The person always comes from the token.
public class ListMyBiddingUseCase {

    private static final int MAX_SIZE = 100;

    private final BidRepositoryPort bidRepositoryPort;
    private final AuctionRepositoryPort auctionRepositoryPort;
    private final AutoBidRepositoryPort autoBidRepositoryPort;

    public ListMyBiddingUseCase(BidRepositoryPort bidRepositoryPort, AuctionRepositoryPort auctionRepositoryPort,
                                AutoBidRepositoryPort autoBidRepositoryPort) {
        this.bidRepositoryPort = bidRepositoryPort;
        this.auctionRepositoryPort = auctionRepositoryPort;
        this.autoBidRepositoryPort = autoBidRepositoryPort;
    }

    public List<MyBiddingItem> list(String bidderId, int page, int size) {
        List<FieldError> errors = new ArrayList<>();
        if (page < 0) {
            errors.add(new FieldError("page", "Page must be greater than or equal to 0"));
        }
        if (size < 1 || size > MAX_SIZE) {
            errors.add(new FieldError("size", "Size must be between 1 and " + MAX_SIZE));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        List<BidderAuctionSummary> summaries = bidRepositoryPort.findAuctionsBidOnBy(bidderId, page, size);
        if (summaries.isEmpty()) {
            return List.of();
        }
        List<String> auctionIds = summaries.stream().map(BidderAuctionSummary::auctionId).toList();
        Map<String, Auction> auctions = auctionRepositoryPort.findAllByIds(auctionIds).stream()
                .collect(Collectors.toMap(Auction::getId, Function.identity()));
        Map<String, AutoBid> autoBids = new HashMap<>();
        autoBidRepositoryPort.findByBidderIdAndAuctionIds(bidderId, auctionIds)
                .forEach(autoBid -> autoBids.put(autoBid.getAuctionId(), autoBid));

        List<MyBiddingItem> items = new ArrayList<>();
        for (BidderAuctionSummary summary : summaries) {
            Auction auction = auctions.get(summary.auctionId());
            if (auction == null) {
                continue;
            }
            AutoBid autoBid = autoBids.get(summary.auctionId());
            items.add(new MyBiddingItem(auction.getId(), auction.getProductId(), auction.getStatus().name(),
                    myStatus(auction, bidderId), auction.getEndTime(), auction.getCurrentHighestBid(),
                    summary.myHighestBid(), autoBid == null ? null : autoBid.getMaxAmount()));
        }
        return items;
    }

    private static String myStatus(Auction auction, String bidderId) {
        return switch (auction.getStatus()) {
            case ACTIVE -> bidderId.equals(auction.getCurrentHighestBidderId()) ? "LEADING" : "OUTBID";
            case ENDED -> bidderId.equals(auction.getWinnerId()) ? "WON" : "LOST";
            case CANCELLED -> "CANCELLED";
            case PENDING -> "PENDING";
        };
    }
}
