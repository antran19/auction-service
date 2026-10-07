package com.nexus.auction.application.usecase;

import com.nexus.auction.application.exception.AuctionNotFoundException;
import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.PaymentGatewayPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.common.core.exception.ConflictException;
import com.nexus.common.core.exception.ForbiddenException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public class ConfirmPaymentUseCase {

    private final AuctionRepositoryPort auctionRepositoryPort;
    private final PaymentGatewayPort paymentGatewayPort;

    public ConfirmPaymentUseCase(AuctionRepositoryPort auctionRepositoryPort,
                                  PaymentGatewayPort paymentGatewayPort) {
        this.auctionRepositoryPort = auctionRepositoryPort;
        this.paymentGatewayPort = paymentGatewayPort;
    }

    @Transactional
    public AuctionResult confirm(String auctionId, String callerId, String sessionId) {
        Auction auction = auctionRepositoryPort.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        if (auction.getWinnerId() == null || !auction.getWinnerId().equals(callerId)) {
            throw new ForbiddenException("NOT_THE_WINNER", "Only the winning bidder can confirm this payment");
        }

        // Idempotent: a page refresh or a duplicate confirm call after the first one succeeded
        // must not re-hit Stripe or fail -- it just reports the already-settled state back.
        if (auction.getPaidAt() != null) {
            return CreateAuctionUseCase.toResult(auction);
        }

        PaymentGatewayPort.PaymentStatus paymentStatus = paymentGatewayPort.retrieveSessionStatus(sessionId);
        if (!paymentStatus.paid()) {
            throw new ConflictException("PAYMENT_NOT_CONFIRMED", "Stripe has not confirmed this payment yet");
        }
        // The session's own metadata must point back at this exact auction and winner -- a
        // caller cannot pay off someone else's auction by passing an unrelated sessionId that
        // happens to be "paid" (e.g. one from an auction they won and genuinely paid for).
        if (!auctionId.equals(paymentStatus.auctionId()) || !callerId.equals(paymentStatus.winnerId())) {
            throw new ConflictException("SESSION_AUCTION_MISMATCH",
                    "This payment session does not belong to this auction/winner");
        }

        Auction saved = auctionRepositoryPort.save(auction.markPaid(Instant.now()));
        return CreateAuctionUseCase.toResult(saved);
    }
}
