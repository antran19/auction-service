package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import com.nexus.common.events.AuctionCancelledEvent;
import org.springframework.transaction.annotation.Transactional;

// fulfillment-service could not hold a unit for this auction: auction-service's copy of the
// stock still showed one in the seconds after it sold. The auction cannot be honoured, so it is
// cancelled by the system. Only a PENDING/ACTIVE auction is touched -- anything else (or an
// unknown id) is left alone, so a late or repeated event cannot undo a finished auction.
public class CancelAuctionForMissingStockUseCase {

    static final String CANCELLED_BY_SYSTEM = "SYSTEM";

    private final AuctionRepositoryPort auctionRepositoryPort;
    private final EventPublisherPort eventPublisherPort;

    public CancelAuctionForMissingStockUseCase(AuctionRepositoryPort auctionRepositoryPort,
                                               EventPublisherPort eventPublisherPort) {
        this.auctionRepositoryPort = auctionRepositoryPort;
        this.eventPublisherPort = eventPublisherPort;
    }

    @Transactional
    public void cancel(String auctionId) {
        Auction auction = auctionRepositoryPort.findByIdForUpdate(auctionId).orElse(null);
        if (auction == null
                || (auction.getStatus() != AuctionStatus.PENDING && auction.getStatus() != AuctionStatus.ACTIVE)) {
            return;
        }
        Auction cancelled = auctionRepositoryPort.save(auction.withStatus(AuctionStatus.CANCELLED));
        eventPublisherPort.publish(new AuctionCancelledEvent(cancelled.getId(), CANCELLED_BY_SYSTEM));
    }
}
