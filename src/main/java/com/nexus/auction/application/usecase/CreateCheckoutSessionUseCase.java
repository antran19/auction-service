package com.nexus.auction.application.usecase;

import com.nexus.auction.application.exception.AuctionNotFoundException;
import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.PaymentGatewayPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import com.nexus.common.core.exception.ConflictException;
import com.nexus.common.core.exception.ForbiddenException;

public class CreateCheckoutSessionUseCase {

    private final AuctionRepositoryPort auctionRepositoryPort;
    private final PaymentGatewayPort paymentGatewayPort;

    public CreateCheckoutSessionUseCase(AuctionRepositoryPort auctionRepositoryPort,
                                         PaymentGatewayPort paymentGatewayPort) {
        this.auctionRepositoryPort = auctionRepositoryPort;
        this.paymentGatewayPort = paymentGatewayPort;
    }

    public PaymentGatewayPort.CheckoutSession createSession(String auctionId, String callerId,
                                                              String successUrl, String cancelUrl) {
        Auction auction = auctionRepositoryPort.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        if (auction.getStatus() != AuctionStatus.ENDED || auction.getWinnerId() == null) {
            throw new ConflictException("AUCTION_NOT_ENDED",
                    "Auction " + auctionId + " has not ended with a winner yet");
        }
        if (!auction.getWinnerId().equals(callerId)) {
            throw new ForbiddenException("NOT_THE_WINNER", "Only the winning bidder can pay for this auction");
        }
        if (auction.getPaidAt() != null) {
            throw new ConflictException("ALREADY_PAID", "Auction " + auctionId + " has already been paid for");
        }

        return paymentGatewayPort.createCheckoutSession(
                auctionId, callerId, auction.getFinalPrice(), successUrl, cancelUrl);
    }
}
