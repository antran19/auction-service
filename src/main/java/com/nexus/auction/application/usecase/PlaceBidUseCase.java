package com.nexus.auction.application.usecase;

import com.nexus.auction.application.exception.AuctionNotFoundException;
import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.AutoBidRepositoryPort;
import com.nexus.auction.application.port.out.BidBroadcastPort;
import com.nexus.auction.application.port.out.BidRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AutoBid;
import com.nexus.auction.domain.model.Bid;
import com.nexus.auction.domain.service.AntiSnipingPolicy;
import com.nexus.auction.domain.service.AutoBidResolver;
import com.nexus.auction.domain.service.BidValidationPolicy;
import com.nexus.common.core.exception.ForbiddenException;
import com.nexus.common.events.BidPlacedEvent;
import com.nexus.common.events.OutbidEvent;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class PlaceBidUseCase {

    private final AuctionRepositoryPort auctionRepositoryPort;
    private final BidRepositoryPort bidRepositoryPort;
    private final EventPublisherPort eventPublisherPort;
    private final BidBroadcastPort bidBroadcastPort;
    private final AutoBidRepositoryPort autoBidRepositoryPort;

    public PlaceBidUseCase(AuctionRepositoryPort auctionRepositoryPort, BidRepositoryPort bidRepositoryPort,
                            EventPublisherPort eventPublisherPort, BidBroadcastPort bidBroadcastPort,
                            AutoBidRepositoryPort autoBidRepositoryPort) {
        this.auctionRepositoryPort = auctionRepositoryPort;
        this.bidRepositoryPort = bidRepositoryPort;
        this.eventPublisherPort = eventPublisherPort;
        this.bidBroadcastPort = bidBroadcastPort;
        this.autoBidRepositoryPort = autoBidRepositoryPort;
    }

    // @Transactional is what makes findByIdForUpdate's SELECT ... FOR UPDATE actually hold the
    // row lock for the duration of this method: the lock is released at commit/rollback, so every
    // concurrent caller for the same auctionId blocks here until the previous one finishes,
    // serializing bid validation + write and eliminating the lost-update race.
    @Transactional
    public BidResult placeBid(String auctionId, String bidderId, BigDecimal amount, String callerTrustLevel) {
        // SRS: MIN_REPUTATION_TO_BID. A null/missing trustLevel (an old token issued before
        // this claim existed, or a test harness not exercising it) fails open rather than
        // blocking every caller -- a real login always carries a real value going forward.
        if ("LOW".equals(callerTrustLevel)) {
            throw new ForbiddenException("INSUFFICIENT_TRUST_TO_BID",
                    "Your reputation score is too low to place bids");
        }

        Auction auction = auctionRepositoryPort.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        BidValidationPolicy.validate(auction, bidderId, amount, Instant.now());

        String previousHighestBidderId = auction.getCurrentHighestBidderId();

        Bid bid = Bid.create(auctionId, bidderId, amount);
        bidRepositoryPort.save(bid);

        Auction updated = auction.withBid(bidderId, amount);
        // Only the bid a person sends can extend the auction. The bids the system places for the
        // standing maximums below never do, otherwise a chain of them could keep extending it.
        Optional<Instant> extendedEndTime = AntiSnipingPolicy.tryExtend(updated, Instant.now());
        if (extendedEndTime.isPresent()) {
            updated = updated.withExtendedEndTime(extendedEndTime.get());
        }

        BidResult result = new BidResult(bid.getId(), auctionId, bidderId, amount, bid.getPlacedAt());
        bidBroadcastPort.bidPlaced(result);

        // The standing maximums answer the bid, inside the same row lock.
        updated = answerWithAutoBids(updated);

        auctionRepositoryPort.save(updated);

        // "You placed a bid" is sent for the bid the person sent, not for each step of the system.
        eventPublisherPort.publish(new BidPlacedEvent(auctionId, bidderId, amount));
        // One "outbid" per person who ends up behind, with the price they were left behind at.
        Set<String> behind = new LinkedHashSet<>();
        if (previousHighestBidderId != null) {
            behind.add(previousHighestBidderId);
        }
        behind.add(bidderId);
        behind.remove(updated.getCurrentHighestBidderId());
        for (String loser : behind) {
            eventPublisherPort.publish(new OutbidEvent(auctionId, loser, updated.getCurrentHighestBid()));
        }

        return result;
    }

    private Auction answerWithAutoBids(Auction auction) {
        List<AutoBid> autoBids = autoBidRepositoryPort.findByAuctionId(auction.getId());
        List<AutoBidResolver.Step> steps = AutoBidResolver.resolve(auction.getStartingPrice(), auction.getBidIncrement(),
                auction.getCurrentHighestBid(), auction.getCurrentHighestBidderId(), autoBids);
        Auction current = auction;
        for (AutoBidResolver.Step step : steps) {
            Bid autoBid = Bid.create(auction.getId(), step.bidderId(), step.amount());
            bidRepositoryPort.save(autoBid);
            current = current.withBid(step.bidderId(), step.amount());
            bidBroadcastPort.bidPlaced(new BidResult(autoBid.getId(), auction.getId(), step.bidderId(),
                    step.amount(), autoBid.getPlacedAt()));
        }
        return current;
    }
}
