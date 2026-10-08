package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import com.nexus.common.events.AuctionPaymentTimeoutEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmitPaymentTimeoutUseCaseTest {

    private AuctionRepositoryPort auctionRepositoryPort;
    private EventPublisherPort eventPublisherPort;
    private EmitPaymentTimeoutUseCase useCase;

    @BeforeEach
    void setUp() {
        auctionRepositoryPort = mock(AuctionRepositoryPort.class);
        eventPublisherPort = mock(EventPublisherPort.class);
        useCase = new EmitPaymentTimeoutUseCase(auctionRepositoryPort, eventPublisherPort);
        when(auctionRepositoryPort.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Auction endedAuctionWithWinnerPastDeadline() {
        Instant now = Instant.now();
        Auction ended = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                        now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS))
                .withStatus(AuctionStatus.ACTIVE)
                .withSettlement("bidder-1", new BigDecimal("150.00"), now.minus(1, ChronoUnit.MINUTES));
        return ended;
    }

    @Test
    void emit_publishesTimeoutForAWinnerPastDeadline() {
        Auction auction = endedAuctionWithWinnerPastDeadline();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        useCase.emit(auction.getId());

        verify(auctionRepositoryPort).save(argThat(Auction::isPaymentTimeoutEmitted));
        verify(eventPublisherPort).publish(any(AuctionPaymentTimeoutEvent.class));
    }

    @Test
    void emit_doesNothingWhenAuctionNotFound() {
        when(auctionRepositoryPort.findByIdForUpdate("missing")).thenReturn(Optional.empty());

        useCase.emit("missing");

        verify(auctionRepositoryPort, never()).save(any());
        verifyNoInteractions(eventPublisherPort);
    }

    @Test
    void emit_doesNothingWhenAlreadyPaid() {
        Auction auction = endedAuctionWithWinnerPastDeadline().markPaid(Instant.now());
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        useCase.emit(auction.getId());

        verify(auctionRepositoryPort, never()).save(any());
        verifyNoInteractions(eventPublisherPort);
    }

    @Test
    void emit_doesNothingWhenThereIsNoWinner() {
        Instant now = Instant.now();
        Auction auction = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS));
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        useCase.emit(auction.getId());

        verify(auctionRepositoryPort, never()).save(any());
        verifyNoInteractions(eventPublisherPort);
    }

    @Test
    void emit_isIdempotentWhenCalledTwiceDirectly() {
        // Exercises the payment_timeout_emitted re-check under the lock directly, rather
        // than only via two overlapping job.run() polls whose second list query already
        // filters the row out (which never actually reaches this guard).
        Auction auction = endedAuctionWithWinnerPastDeadline();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId()))
                .thenReturn(Optional.of(auction))
                .thenReturn(Optional.of(auction.withPaymentTimeoutEmitted()));

        useCase.emit(auction.getId());
        useCase.emit(auction.getId());

        verify(eventPublisherPort, times(1)).publish(any(AuctionPaymentTimeoutEvent.class));
    }
}
