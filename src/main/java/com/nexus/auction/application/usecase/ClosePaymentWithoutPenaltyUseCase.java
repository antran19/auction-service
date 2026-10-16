package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.domain.model.Auction;
import org.springframework.transaction.annotation.Transactional;

// The winner's order was cancelled for a reason that is NOT the winner's fault (e.g. the
// seller had no stock left). The winner can no longer pay, so PaymentDeadlineJob must not
// punish them 24h later -- but no AuctionPaymentTimeoutEvent is published either, so
// user-service never applies a penalty. Setting payment_timeout_emitted is how the job is told
// "this auction's payment deadline has been dealt with"; the column means "handled", not
// strictly "a timeout event went out".
public class ClosePaymentWithoutPenaltyUseCase {

    private final AuctionRepositoryPort auctionRepositoryPort;

    public ClosePaymentWithoutPenaltyUseCase(AuctionRepositoryPort auctionRepositoryPort) {
        this.auctionRepositoryPort = auctionRepositoryPort;
    }

    @Transactional
    public void close(String auctionId) {
        Auction auction = auctionRepositoryPort.findByIdForUpdate(auctionId).orElse(null);
        // Same re-check under the lock as EmitPaymentTimeoutUseCase: already handled or paid -> no-op.
        if (auction == null || auction.isPaymentTimeoutEmitted() || auction.getPaidAt() != null) {
            return;
        }
        auctionRepositoryPort.save(auction.withPaymentTimeoutEmitted());
    }
}
