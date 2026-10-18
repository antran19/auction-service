package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import com.nexus.common.events.AuctionCancelledEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class CancelAuctionForMissingStockUseCaseTest {

    private final AuctionRepositoryPort auctionRepositoryPort = mock(AuctionRepositoryPort.class);
    private final EventPublisherPort eventPublisherPort = mock(EventPublisherPort.class);
    private final CancelAuctionForMissingStockUseCase useCase =
            new CancelAuctionForMissingStockUseCase(auctionRepositoryPort, eventPublisherPort);

    @BeforeEach
    void setUp() {
        when(auctionRepositoryPort.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Auction auction(AuctionStatus status) {
        Instant start = Instant.now().plus(1, ChronoUnit.MINUTES);
        return Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                start, start.plus(2, ChronoUnit.HOURS)).withStatus(status);
    }

    @Test
    void cancel_aPendingOrActiveAuction_cancelsItAsTheSystem() {
        Auction pending = auction(AuctionStatus.PENDING);
        when(auctionRepositoryPort.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));

        useCase.cancel(pending.getId());

        verify(auctionRepositoryPort).save(argThat(a -> a.getStatus() == AuctionStatus.CANCELLED));
        verify(eventPublisherPort).publish(argThat(e -> e instanceof AuctionCancelledEvent evt
                && evt.getAuctionId().equals(pending.getId()) && "SYSTEM".equals(evt.getCancelledBy())));
    }

    @Test
    void cancel_anEndedOrAlreadyCancelledAuction_isANoOp() {
        Auction ended = auction(AuctionStatus.ENDED);
        Auction cancelled = auction(AuctionStatus.CANCELLED);
        when(auctionRepositoryPort.findByIdForUpdate(ended.getId())).thenReturn(Optional.of(ended));
        when(auctionRepositoryPort.findByIdForUpdate(cancelled.getId())).thenReturn(Optional.of(cancelled));

        useCase.cancel(ended.getId());
        useCase.cancel(cancelled.getId());
        useCase.cancel("unknown");

        verify(auctionRepositoryPort, never()).save(any());
        verify(eventPublisherPort, never()).publish(any());
    }
}
