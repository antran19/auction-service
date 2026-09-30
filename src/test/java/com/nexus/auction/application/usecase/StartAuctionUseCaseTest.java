package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import com.nexus.common.events.AuctionStartedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StartAuctionUseCaseTest {

    private AuctionRepositoryPort auctionRepositoryPort;
    private EventPublisherPort eventPublisherPort;
    private StartAuctionUseCase useCase;

    @BeforeEach
    void setUp() {
        auctionRepositoryPort = mock(AuctionRepositoryPort.class);
        eventPublisherPort = mock(EventPublisherPort.class);
        useCase = new StartAuctionUseCase(auctionRepositoryPort, eventPublisherPort);
        when(auctionRepositoryPort.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Auction pendingAuctionReadyToStart() {
        Instant now = Instant.now();
        return Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                now.minus(1, ChronoUnit.MINUTES), now.plus(1, ChronoUnit.HOURS));
    }

    @Test
    void start_activatesAPendingAuctionWhoseStartTimeHasPassed() {
        Auction auction = pendingAuctionReadyToStart();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        useCase.start(auction.getId());

        verify(auctionRepositoryPort).save(argThat(a -> a.getStatus() == AuctionStatus.ACTIVE));
        verify(eventPublisherPort).publish(any(AuctionStartedEvent.class));
    }

    @Test
    void start_doesNothingWhenAuctionNotFound() {
        when(auctionRepositoryPort.findByIdForUpdate("missing")).thenReturn(Optional.empty());

        useCase.start("missing");

        verify(auctionRepositoryPort, never()).save(any());
        verifyNoInteractions(eventPublisherPort);
    }

    @Test
    void start_doesNothingWhenStatusIsNotPending() {
        Auction active = pendingAuctionReadyToStart().withStatus(AuctionStatus.ACTIVE);
        when(auctionRepositoryPort.findByIdForUpdate(active.getId())).thenReturn(Optional.of(active));

        useCase.start(active.getId());

        verify(auctionRepositoryPort, never()).save(any());
        verifyNoInteractions(eventPublisherPort);
    }

    @Test
    void start_doesNothingWhenStartTimeIsStillInTheFuture() {
        // Guards against a race with UpdateAuctionUseCase: the job's list query found this
        // auction PENDING with start_time <= now, but an update pushed start_time into the
        // future before this use case acquired the row lock. Re-check under the lock, not
        // just status, or the auction activates too early.
        Instant now = Instant.now();
        Auction auction = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                now.plus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS));
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        useCase.start(auction.getId());

        verify(auctionRepositoryPort, never()).save(any());
        verifyNoInteractions(eventPublisherPort);
    }

    @Test
    void start_isIdempotentWhenCalledTwiceDirectly() {
        Auction auction = pendingAuctionReadyToStart();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId()))
                .thenReturn(Optional.of(auction))
                .thenReturn(Optional.of(auction.withStatus(AuctionStatus.ACTIVE)));

        useCase.start(auction.getId());
        useCase.start(auction.getId());

        verify(eventPublisherPort, times(1)).publish(any(AuctionStartedEvent.class));
    }
}
