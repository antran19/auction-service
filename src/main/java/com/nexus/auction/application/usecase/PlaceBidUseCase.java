package com.nexus.auction.application.usecase;

import com.nexus.auction.application.exception.AuctionNotFoundException;
import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.BidRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.Bid;
import com.nexus.auction.domain.service.AntiSnipingPolicy;
import com.nexus.auction.domain.service.BidValidationPolicy;
import com.nexus.common.events.BidPlacedEvent;
import com.nexus.common.events.OutbidEvent;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public class PlaceBidUseCase {

    private final AuctionRepositoryPort auctionRepositoryPort;
    private final BidRepositoryPort bidRepositoryPort;
    private final EventPublisherPort eventPublisherPort;

    public PlaceBidUseCase(AuctionRepositoryPort auctionRepositoryPort, BidRepositoryPort bidRepositoryPort,
                            EventPublisherPort eventPublisherPort) {
        this.auctionRepositoryPort = auctionRepositoryPort;
        this.bidRepositoryPort = bidRepositoryPort;
        this.eventPublisherPort = eventPublisherPort;
    }

    // @Transactional is what makes findByIdForUpdate's SELECT ... FOR UPDATE actually hold the
    // row lock for the duration of this method: the lock is released at commit/rollback, so every
    // concurrent caller for the same auctionId blocks here until the previous one finishes,
    // serializing bid validation + write and eliminating the lost-update race.
    @Transactional
    public BidResult placeBid(String auctionId, String bidderId, BigDecimal amount) {
        Auction auction = auctionRepositoryPort.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        BidValidationPolicy.validate(auction, bidderId, amount, Instant.now());

        String previousHighestBidderId = auction.getCurrentHighestBidderId();

        Bid bid = Bid.create(auctionId, bidderId, amount);
        bidRepositoryPort.save(bid);

        Auction updated = auction.withBid(bidderId, amount);
        Optional<Instant> extendedEndTime = AntiSnipingPolicy.tryExtend(updated, Instant.now());
        if (extendedEndTime.isPresent()) {
            updated = updated.withExtendedEndTime(extendedEndTime.get());
        }
        auctionRepositoryPort.save(updated);

        eventPublisherPort.publish(new BidPlacedEvent(auctionId, bidderId, amount));
        if (previousHighestBidderId != null && !previousHighestBidderId.equals(bidderId)) {
            eventPublisherPort.publish(new OutbidEvent(auctionId, previousHighestBidderId, amount));
        }

        return new BidResult(bid.getId(), auctionId, bidderId, amount, bid.getPlacedAt());
    }
}
