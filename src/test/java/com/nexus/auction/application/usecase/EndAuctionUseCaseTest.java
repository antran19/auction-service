package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import com.nexus.common.events.AuctionEndedEvent;
import com.nexus.common.events.AuctionFailedEvent;
import com.nexus.common.events.AuctionWonEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EndAuctionUseCaseTest {

    private AuctionRepositoryPort auctionRepositoryPort;
    private EventPublisherPort eventPublisherPort;
    private EndAuctionUseCase useCase;

    @BeforeEach
    void setUp() {
        auctionRepositoryPort = mock(AuctionRepositoryPort.class);
        eventPublisherPort = mock(EventPublisherPort.class);
        useCase = new EndAuctionUseCase(auctionRepositoryPort, eventPublisherPort);
        when(auctionRepositoryPort.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Auction activeAuctionPastEndTime() {
        Instant now = Instant.now();
        return Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                        now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.MINUTES))
                .withStatus(AuctionStatus.ACTIVE);
    }

    @Test
    void end_settlesAnAuctionWithAWinner() {
        Auction auction = activeAuctionPastEndTime().withBid("bidder-1", new BigDecimal("150.00"));
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        useCase.end(auction.getId());

        verify(auctionRepositoryPort).save(argThat(a ->
                a.getStatus() == AuctionStatus.ENDED && "bidder-1".equals(a.getWinnerId())));
        verify(eventPublisherPort).publish(any(AuctionEndedEvent.class));
        verify(eventPublisherPort).publish(any(AuctionWonEvent.class));
    }

    @Test
    void end_marksAuctionFailedWhenThereWereNoBids() {
        Auction auction = activeAuctionPastEndTime();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        useCase.end(auction.getId());

        verify(eventPublisherPort).publish(any(AuctionEndedEvent.class));
        verify(eventPublisherPort).publish(any(AuctionFailedEvent.class));
    }

    @Test
    void end_doesNothingWhenAuctionNotFound() {
        when(auctionRepositoryPort.findByIdForUpdate("missing")).thenReturn(Optional.empty());

        useCase.end("missing");

        verify(auctionRepositoryPort, never()).save(any());
        verifyNoInteractions(eventPublisherPort);
    }

    @Test
    void end_doesNothingWhenStatusIsNotActive() {
        Auction pending = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                Instant.now().plus(1, ChronoUnit.HOURS), Instant.now().plus(2, ChronoUnit.HOURS));
        when(auctionRepositoryPort.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));

        useCase.end(pending.getId());

        verify(auctionRepositoryPort, never()).save(any());
        verifyNoInteractions(eventPublisherPort);
    }

    @Test
    void end_doesNothingWhenEndTimeIsStillInTheFuture() {
        // Guards against a race: the job's list query found this auction ACTIVE with
        // end_time <= now, but a bid's anti-sniping extension pushed end_time forward
        // before this use case acquired the row lock. Re-check end_time under the lock,
        // not just status, or the auction ends early and ignores the extension it just
        // legitimately earned.
        Instant now = Instant.now();
        Auction auction = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                        now.minus(1, ChronoUnit.HOURS), now.plus(5, ChronoUnit.MINUTES))
                .withStatus(AuctionStatus.ACTIVE);
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        useCase.end(auction.getId());

        verify(auctionRepositoryPort, never()).save(any());
        verifyNoInteractions(eventPublisherPort);
    }

    @Test
    void end_isIdempotentWhenCalledTwiceDirectly() {
        Auction auction = activeAuctionPastEndTime().withBid("bidder-1", new BigDecimal("150.00"));
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId()))
                .thenReturn(Optional.of(auction))
                .thenReturn(Optional.of(auction.withSettlement("bidder-1", new BigDecimal("150.00"), Instant.now())));

        useCase.end(auction.getId());
        useCase.end(auction.getId());

        verify(eventPublisherPort, times(1)).publish(any(AuctionEndedEvent.class));
        verify(eventPublisherPort, times(1)).publish(any(AuctionWonEvent.class));
    }
}
