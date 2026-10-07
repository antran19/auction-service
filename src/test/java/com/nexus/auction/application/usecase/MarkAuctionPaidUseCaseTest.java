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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MarkAuctionPaidUseCaseTest {

    private AuctionRepositoryPort auctionRepositoryPort;
    private MarkAuctionPaidUseCase useCase;

    @BeforeEach
    void setUp() {
        auctionRepositoryPort = mock(AuctionRepositoryPort.class);
        useCase = new MarkAuctionPaidUseCase(auctionRepositoryPort);
        when(auctionRepositoryPort.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Auction endedAuctionWithWinner(String winnerId) {
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS);
        return Auction.reconstitute("auction-1", "product-1", "seller-1", new BigDecimal("100.00"),
                new BigDecimal("10.00"), new BigDecimal("150.00"), winnerId, AuctionStatus.ENDED, start,
                start.plus(1, ChronoUnit.HOURS), 0, winnerId, new BigDecimal("150.00"),
                start.plus(24, ChronoUnit.HOURS), false, null, start, start);
    }

    @Test
    void markPaid_setsAuctionPaidAtWhenWinnerMatches() {
        when(auctionRepositoryPort.findByIdForUpdate("auction-1"))
                .thenReturn(Optional.of(endedAuctionWithWinner("winner-1")));

        useCase.markPaid("auction-1", "winner-1");

        verify(auctionRepositoryPort).save(argThat(a -> a.getPaidAt() != null));
    }

    @Test
    void markPaid_isNoOpWhenAuctionNotFound() {
        when(auctionRepositoryPort.findByIdForUpdate("missing")).thenReturn(Optional.empty());

        useCase.markPaid("missing", "winner-1");

        verify(auctionRepositoryPort, never()).save(any());
    }

    @Test
    void markPaid_isNoOpWhenWinnerIdDoesNotMatch() {
        when(auctionRepositoryPort.findByIdForUpdate("auction-1"))
                .thenReturn(Optional.of(endedAuctionWithWinner("winner-1")));

        useCase.markPaid("auction-1", "someone-else");

        verify(auctionRepositoryPort, never()).save(any());
    }

    @Test
    void markPaid_isIdempotentWhenAlreadyPaid() {
        Auction alreadyPaid = endedAuctionWithWinner("winner-1").markPaid(Instant.now());
        when(auctionRepositoryPort.findByIdForUpdate("auction-1")).thenReturn(Optional.of(alreadyPaid));

        useCase.markPaid("auction-1", "winner-1");

        verify(auctionRepositoryPort, never()).save(any());
    }
}
