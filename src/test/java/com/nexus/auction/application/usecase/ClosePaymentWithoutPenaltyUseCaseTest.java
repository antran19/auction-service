package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClosePaymentWithoutPenaltyUseCaseTest {

    private AuctionRepositoryPort auctionRepositoryPort;
    private ClosePaymentWithoutPenaltyUseCase useCase;

    @BeforeEach
    void setUp() {
        auctionRepositoryPort = mock(AuctionRepositoryPort.class);
        useCase = new ClosePaymentWithoutPenaltyUseCase(auctionRepositoryPort);
        when(auctionRepositoryPort.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Auction endedAuctionWithWinner() {
        Instant now = Instant.now();
        return Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                        now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS))
                .withStatus(AuctionStatus.ACTIVE)
                .withSettlement("bidder-1", new BigDecimal("150.00"), now.plus(1, ChronoUnit.DAYS));
    }

    @Test
    void close_flagsDeadlineHandledSoTheJobNeverPenalizes() {
        Auction auction = endedAuctionWithWinner();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        useCase.close(auction.getId());

        verify(auctionRepositoryPort).save(argThat(Auction::isPaymentTimeoutEmitted));
    }

    @Test
    void close_isNoOpWhenAlreadyHandled() {
        Auction auction = endedAuctionWithWinner().withPaymentTimeoutEmitted();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        useCase.close(auction.getId());

        verify(auctionRepositoryPort, never()).save(any());
    }

    @Test
    void close_isNoOpWhenAlreadyPaid() {
        Auction auction = endedAuctionWithWinner().markPaid(Instant.now());
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        useCase.close(auction.getId());

        verify(auctionRepositoryPort, never()).save(any());
    }

    @Test
    void close_isNoOpForUnknownAuction() {
        when(auctionRepositoryPort.findByIdForUpdate("missing")).thenReturn(Optional.empty());

        useCase.close("missing");

        verify(auctionRepositoryPort, never()).save(any());
    }
}
