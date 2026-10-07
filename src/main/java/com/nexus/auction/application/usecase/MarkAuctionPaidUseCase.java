package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.domain.model.Auction;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

// Replaces the old HTTP-driven ConfirmPaymentUseCase: commerce-service now owns Checkout &
// Payment entirely, and tells auction-service a winner has paid by emitting OrderPaid over
// Kafka instead of auction-service calling Stripe itself. This keeps PaymentDeadlineJob
// working unchanged -- it only ever reads Auction.paidAt, not how it got set.
public class MarkAuctionPaidUseCase {

    private final AuctionRepositoryPort auctionRepositoryPort;

    public MarkAuctionPaidUseCase(AuctionRepositoryPort auctionRepositoryPort) {
        this.auctionRepositoryPort = auctionRepositoryPort;
    }

    @Transactional
    public void markPaid(String auctionId, String winnerId) {
        Auction auction = auctionRepositoryPort.findByIdForUpdate(auctionId).orElse(null);
        if (auction == null) {
            return;
        }
        // Idempotent (Kafka is at-least-once) and defensive against a mismatched winner id in
        // a malformed/forged event -- silently ignored rather than throwing, since this is a
        // background Kafka listener with no caller to report an error back to.
        if (auction.getPaidAt() != null || !winnerId.equals(auction.getWinnerId())) {
            return;
        }
        auctionRepositoryPort.save(auction.markPaid(Instant.now()));
    }
}
