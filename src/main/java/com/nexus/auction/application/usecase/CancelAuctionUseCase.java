package com.nexus.auction.application.usecase;

import com.nexus.auction.application.exception.AuctionNotFoundException;
import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import com.nexus.common.core.exception.ConflictException;
import com.nexus.common.events.AuctionCancelledEvent;
import org.springframework.transaction.annotation.Transactional;

public class CancelAuctionUseCase {

    private final AuctionRepositoryPort auctionRepositoryPort;
    private final EventPublisherPort eventPublisherPort;

    public CancelAuctionUseCase(AuctionRepositoryPort auctionRepositoryPort, EventPublisherPort eventPublisherPort) {
        this.auctionRepositoryPort = auctionRepositoryPort;
        this.eventPublisherPort = eventPublisherPort;
    }

    @Transactional
    public AuctionResult cancel(String id, String callerId) {
        Auction existing = auctionRepositoryPort.findByIdForUpdate(id).orElseThrow(() -> new AuctionNotFoundException(id));
        AuctionOwnershipPolicy.requireOwner(existing, callerId);

        boolean eligible = existing.getStatus() == AuctionStatus.PENDING
                || (existing.getStatus() == AuctionStatus.ACTIVE && existing.getCurrentHighestBid() == null);
        if (!eligible) {
            throw new ConflictException("AUCTION_NOT_CANCELLABLE",
                    "Only a pending auction, or an active auction with no bids, can be cancelled by its seller: " + id);
        }

        Auction cancelled = auctionRepositoryPort.save(existing.withStatus(AuctionStatus.CANCELLED));
        eventPublisherPort.publish(new AuctionCancelledEvent(cancelled.getId(), callerId));
        return CreateAuctionUseCase.toResult(cancelled);
    }
}
