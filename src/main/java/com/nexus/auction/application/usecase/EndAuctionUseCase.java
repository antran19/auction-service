package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import com.nexus.common.events.AuctionEndedEvent;
import com.nexus.common.events.AuctionFailedEvent;
import com.nexus.common.events.AuctionWonEvent;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class EndAuctionUseCase {

    private static final long PAYMENT_DEADLINE_HOURS = 24;

    private final AuctionRepositoryPort auctionRepositoryPort;
    private final EventPublisherPort eventPublisherPort;

    public EndAuctionUseCase(AuctionRepositoryPort auctionRepositoryPort, EventPublisherPort eventPublisherPort) {
        this.auctionRepositoryPort = auctionRepositoryPort;
        this.eventPublisherPort = eventPublisherPort;
    }

    @Transactional
    public void end(String auctionId) {
        Auction auction = auctionRepositoryPort.findByIdForUpdate(auctionId).orElse(null);
        // Same idempotency guard as StartAuctionUseCase: if this auction was already ended by an
        // earlier poll tick (or a prior call within this same tick), do nothing.
        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE) {
            return;
        }
        // Re-check end_time too, not just status: a bid's anti-sniping extension may have
        // pushed end_time forward after the job's list query found this auction "ready to
        // end" but before this use case acquired the row lock.
        if (auction.getEndTime().isAfter(Instant.now())) {
            return;
        }

        boolean hasWinner = auction.getCurrentHighestBidderId() != null;
        Auction ended;
        if (hasWinner) {
            Instant paymentDeadline = Instant.now().plus(PAYMENT_DEADLINE_HOURS, ChronoUnit.HOURS);
            ended = auction.withSettlement(auction.getCurrentHighestBidderId(), auction.getCurrentHighestBid(), paymentDeadline);
        } else {
            ended = auction.withStatus(AuctionStatus.ENDED);
        }
        Auction saved = auctionRepositoryPort.save(ended);

        eventPublisherPort.publish(new AuctionEndedEvent(saved.getId()));
        if (hasWinner) {
            eventPublisherPort.publish(new AuctionWonEvent(saved.getId(), saved.getProductId(), saved.getSellerId(),
                    saved.getWinnerId(), saved.getFinalPrice()));
        } else {
            eventPublisherPort.publish(new AuctionFailedEvent(saved.getId()));
        }
    }
}
