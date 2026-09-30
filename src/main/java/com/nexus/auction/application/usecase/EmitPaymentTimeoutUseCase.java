package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.common.events.AuctionPaymentTimeoutEvent;
import org.springframework.transaction.annotation.Transactional;

public class EmitPaymentTimeoutUseCase {

    private final AuctionRepositoryPort auctionRepositoryPort;
    private final EventPublisherPort eventPublisherPort;

    public EmitPaymentTimeoutUseCase(AuctionRepositoryPort auctionRepositoryPort, EventPublisherPort eventPublisherPort) {
        this.auctionRepositoryPort = auctionRepositoryPort;
        this.eventPublisherPort = eventPublisherPort;
    }

    @Transactional
    public void emit(String auctionId) {
        Auction auction = auctionRepositoryPort.findByIdForUpdate(auctionId).orElse(null);
        // The payment_timeout_emitted flag (checked again here, under the lock, not just in the
        // job's list query) is what makes this safe to call twice for the same auction across
        // overlapping poll ticks: once true, this is a no-op forever.
        if (auction == null || auction.isPaymentTimeoutEmitted() || auction.getWinnerId() == null) {
            return;
        }

        Auction flagged = auctionRepositoryPort.save(auction.withPaymentTimeoutEmitted());
        eventPublisherPort.publish(new AuctionPaymentTimeoutEvent(flagged.getId(), flagged.getWinnerId()));
    }
}
