package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import com.nexus.common.events.AuctionStartedEvent;
import org.springframework.transaction.annotation.Transactional;

public class StartAuctionUseCase {

    private final AuctionRepositoryPort auctionRepositoryPort;
    private final EventPublisherPort eventPublisherPort;

    public StartAuctionUseCase(AuctionRepositoryPort auctionRepositoryPort, EventPublisherPort eventPublisherPort) {
        this.auctionRepositoryPort = auctionRepositoryPort;
        this.eventPublisherPort = eventPublisherPort;
    }

    @Transactional
    public void start(String auctionId) {
        Auction auction = auctionRepositoryPort.findByIdForUpdate(auctionId).orElse(null);
        // Re-check under the lock: another poll tick (or this one, racing a manual admin action)
        // may have already moved this auction out of PENDING between the job's list query and
        // this per-row call — silently no-op rather than re-activating or erroring.
        if (auction == null || auction.getStatus() != AuctionStatus.PENDING) {
            return;
        }

        Auction started = auctionRepositoryPort.save(auction.withStatus(AuctionStatus.ACTIVE));
        eventPublisherPort.publish(new AuctionStartedEvent(started.getId()));
    }
}
