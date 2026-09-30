package com.nexus.auction.application.usecase;

import com.nexus.auction.application.exception.AuctionNotFoundException;
import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import com.nexus.common.core.exception.ConflictException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

public class UpdateAuctionUseCase {

    private final AuctionRepositoryPort auctionRepositoryPort;

    public UpdateAuctionUseCase(AuctionRepositoryPort auctionRepositoryPort) {
        this.auctionRepositoryPort = auctionRepositoryPort;
    }

    @Transactional
    public AuctionResult update(String id, BigDecimal startingPrice, BigDecimal bidIncrement,
                                 Instant startTime, Instant endTime, String callerId) {
        Auction existing = auctionRepositoryPort.findByIdForUpdate(id).orElseThrow(() -> new AuctionNotFoundException(id));
        AuctionOwnershipPolicy.requireOwner(existing, callerId);
        if (existing.getStatus() != AuctionStatus.PENDING) {
            throw new ConflictException("AUCTION_NOT_EDITABLE",
                    "Only a pending auction can be updated: " + id);
        }

        Auction updated = auctionRepositoryPort.save(existing.withDetails(startingPrice, bidIncrement, startTime, endTime));
        return CreateAuctionUseCase.toResult(updated);
    }
}
