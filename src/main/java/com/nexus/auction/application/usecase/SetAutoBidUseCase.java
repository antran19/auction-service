package com.nexus.auction.application.usecase;

import com.nexus.auction.application.exception.AuctionNotFoundException;
import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.AutoBidRepositoryPort;
import com.nexus.auction.application.port.out.BidBroadcastPort;
import com.nexus.auction.application.port.out.BidRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AutoBid;
import com.nexus.auction.domain.service.BidValidationPolicy;
import com.nexus.common.core.exception.ConflictException;
import com.nexus.common.core.exception.ForbiddenException;
import com.nexus.common.events.OutbidEvent;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

// Stores a bidder's maximum for one auction and, in the same transaction, places the bids that
// follow from it (see AutoBidResponder and AutoBidResolver).
//
// Rules:
//  - the maximum must be a bid the auction would accept (same checks as PlaceBidUseCase: active,
//    not ended, not the seller, at least the minimum bid);
//  - a maximum can be raised but never lowered: other bidders react to it, and lowering it after
//    the price was pushed up would allow shill bidding. Sending the same value again is a no-op.
public class SetAutoBidUseCase {

    private final AuctionRepositoryPort auctionRepositoryPort;
    private final AutoBidRepositoryPort autoBidRepositoryPort;
    private final EventPublisherPort eventPublisherPort;
    private final AutoBidResponder autoBidResponder;

    public SetAutoBidUseCase(AuctionRepositoryPort auctionRepositoryPort, AutoBidRepositoryPort autoBidRepositoryPort,
                             BidRepositoryPort bidRepositoryPort, BidBroadcastPort bidBroadcastPort,
                             EventPublisherPort eventPublisherPort) {
        this.auctionRepositoryPort = auctionRepositoryPort;
        this.autoBidRepositoryPort = autoBidRepositoryPort;
        this.eventPublisherPort = eventPublisherPort;
        this.autoBidResponder = new AutoBidResponder(bidRepositoryPort, autoBidRepositoryPort, bidBroadcastPort);
    }

    // The same lock as PlaceBidUseCase: while a bid is being decided on this auction, nobody can
    // change a maximum, and the other way round.
    @Transactional
    public AutoBidResult setAutoBid(String auctionId, String bidderId, BigDecimal maxAmount, String callerTrustLevel) {
        if ("LOW".equals(callerTrustLevel)) {
            throw new ForbiddenException("INSUFFICIENT_TRUST_TO_BID",
                    "Your reputation score is too low to place bids");
        }

        Auction auction = auctionRepositoryPort.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        BidValidationPolicy.validate(auction, bidderId, maxAmount, Instant.now());

        AutoBid saved = autoBidRepositoryPort.findByAuctionIdAndBidderId(auctionId, bidderId)
                .map(existing -> raise(existing, maxAmount))
                .orElseGet(() -> autoBidRepositoryPort.save(AutoBid.create(auctionId, bidderId, maxAmount)));

        // The new maximum may already be enough to lead, or to answer the bid that leads. Setting
        // a maximum never extends the auction, only a bid a person sends does.
        String previousLeader = auction.getCurrentHighestBidderId();
        Auction updated = autoBidResponder.answer(auction);
        if (updated != auction) {
            auctionRepositoryPort.save(updated);
            // One "outbid" for each person who ends up behind: the former leader, and the person
            // who set the maximum if it was not enough to lead.
            Set<String> behind = new LinkedHashSet<>();
            if (previousLeader != null) {
                behind.add(previousLeader);
            }
            behind.add(bidderId);
            behind.remove(updated.getCurrentHighestBidderId());
            for (String loser : behind) {
                eventPublisherPort.publish(new OutbidEvent(auctionId, loser, updated.getCurrentHighestBid()));
            }
        }

        return new AutoBidResult(saved.getId(), saved.getAuctionId(), saved.getBidderId(), saved.getMaxAmount(),
                saved.getUpdatedAt());
    }

    private AutoBid raise(AutoBid existing, BigDecimal maxAmount) {
        int comparison = maxAmount.compareTo(existing.getMaxAmount());
        if (comparison < 0) {
            throw new ConflictException("AUTO_BID_CANNOT_BE_LOWERED",
                    "Your maximum for this auction is " + existing.getMaxAmount() + " and cannot be lowered");
        }
        if (comparison == 0) {
            return existing;
        }
        return autoBidRepositoryPort.save(existing.withMaxAmount(maxAmount));
    }
}
