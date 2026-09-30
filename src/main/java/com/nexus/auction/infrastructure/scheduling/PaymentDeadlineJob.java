package com.nexus.auction.infrastructure.scheduling;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.usecase.EmitPaymentTimeoutUseCase;
import com.nexus.auction.domain.model.Auction;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PaymentDeadlineJob {

    private final AuctionRepositoryPort auctionRepositoryPort;
    private final EmitPaymentTimeoutUseCase emitPaymentTimeoutUseCase;

    public PaymentDeadlineJob(AuctionRepositoryPort auctionRepositoryPort,
                               EmitPaymentTimeoutUseCase emitPaymentTimeoutUseCase) {
        this.auctionRepositoryPort = auctionRepositoryPort;
        this.emitPaymentTimeoutUseCase = emitPaymentTimeoutUseCase;
    }

    @Scheduled(fixedDelay = 30000)
    public void run() {
        Instant now = Instant.now();
        for (Auction auction : auctionRepositoryPort.findEndedAwaitingPaymentTimeout(now)) {
            emitPaymentTimeoutUseCase.emit(auction.getId());
        }
    }
}
