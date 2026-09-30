package com.nexus.auction.infrastructure.scheduling;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.usecase.EndAuctionUseCase;
import com.nexus.auction.application.usecase.StartAuctionUseCase;
import com.nexus.auction.domain.model.Auction;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AuctionLifecycleJob {

    private final AuctionRepositoryPort auctionRepositoryPort;
    private final StartAuctionUseCase startAuctionUseCase;
    private final EndAuctionUseCase endAuctionUseCase;

    public AuctionLifecycleJob(AuctionRepositoryPort auctionRepositoryPort, StartAuctionUseCase startAuctionUseCase,
                                EndAuctionUseCase endAuctionUseCase) {
        this.auctionRepositoryPort = auctionRepositoryPort;
        this.startAuctionUseCase = startAuctionUseCase;
        this.endAuctionUseCase = endAuctionUseCase;
    }

    @Scheduled(fixedDelay = 10000)
    public void run() {
        Instant now = Instant.now();
        for (Auction auction : auctionRepositoryPort.findPendingReadyToStart(now)) {
            startAuctionUseCase.start(auction.getId());
        }
        for (Auction auction : auctionRepositoryPort.findActiveReadyToEnd(now)) {
            endAuctionUseCase.end(auction.getId());
        }
    }
}
