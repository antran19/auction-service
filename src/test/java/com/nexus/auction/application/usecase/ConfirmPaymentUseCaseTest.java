package com.nexus.auction.application.usecase;

import com.nexus.auction.application.exception.AuctionNotFoundException;
import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.PaymentGatewayPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import com.nexus.common.core.exception.ConflictException;
import com.nexus.common.core.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConfirmPaymentUseCaseTest {

    private AuctionRepositoryPort auctionRepositoryPort;
    private PaymentGatewayPort paymentGatewayPort;
    private ConfirmPaymentUseCase useCase;

    @BeforeEach
    void setUp() {
        auctionRepositoryPort = mock(AuctionRepositoryPort.class);
        paymentGatewayPort = mock(PaymentGatewayPort.class);
        useCase = new ConfirmPaymentUseCase(auctionRepositoryPort, paymentGatewayPort);
    }

    private Auction endedAuctionWonBy(String winnerId) {
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS);
        Auction pending = Auction.reconstitute("auction-1", "product-1", "seller-1",
                new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("150.00"), winnerId,
                AuctionStatus.PENDING, start, start.plus(1, ChronoUnit.HOURS),
                0, null, null, null, false, null, start, start);
        return pending.withSettlement(winnerId, new BigDecimal("150.00"), Instant.now().plus(24, ChronoUnit.HOURS));
    }

    @Test
    void confirm_marksAuctionPaidWhenStripeConfirmsPaymentForThisAuctionAndWinner() {
        Auction auction = endedAuctionWonBy("winner-1");
        when(auctionRepositoryPort.findByIdForUpdate("auction-1")).thenReturn(Optional.of(auction));
        when(paymentGatewayPort.retrieveSessionStatus("sess-1"))
                .thenReturn(new PaymentGatewayPort.PaymentStatus(true, "auction-1", "winner-1"));
        when(auctionRepositoryPort.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));

        AuctionResult result = useCase.confirm("auction-1", "winner-1", "sess-1");

        assertThat(result.paidAt()).isNotNull();
        verify(auctionRepositoryPort).save(argThat(a -> a.getPaidAt() != null));
    }

    @Test
    void confirm_isIdempotent_returnsExistingResultWithoutCallingStripeAgain() {
        Auction alreadyPaid = endedAuctionWonBy("winner-1").markPaid(Instant.now());
        when(auctionRepositoryPort.findByIdForUpdate("auction-1")).thenReturn(Optional.of(alreadyPaid));

        AuctionResult result = useCase.confirm("auction-1", "winner-1", "sess-1");

        assertThat(result.paidAt()).isNotNull();
        verifyNoInteractions(paymentGatewayPort);
        verify(auctionRepositoryPort, never()).save(any());
    }

    @Test
    void confirm_rejectsWhenStripeSaysNotPaid() {
        Auction auction = endedAuctionWonBy("winner-1");
        when(auctionRepositoryPort.findByIdForUpdate("auction-1")).thenReturn(Optional.of(auction));
        when(paymentGatewayPort.retrieveSessionStatus("sess-1"))
                .thenReturn(new PaymentGatewayPort.PaymentStatus(false, "auction-1", "winner-1"));

        assertThatThrownBy(() -> useCase.confirm("auction-1", "winner-1", "sess-1"))
                .isInstanceOf(ConflictException.class);

        verify(auctionRepositoryPort, never()).save(any());
    }

    @Test
    void confirm_rejectsSessionBelongingToADifferentAuction_preventsCrossAuctionReplay() {
        Auction auction = endedAuctionWonBy("winner-1");
        when(auctionRepositoryPort.findByIdForUpdate("auction-1")).thenReturn(Optional.of(auction));
        when(paymentGatewayPort.retrieveSessionStatus("sess-1"))
                .thenReturn(new PaymentGatewayPort.PaymentStatus(true, "some-other-auction", "winner-1"));

        assertThatThrownBy(() -> useCase.confirm("auction-1", "winner-1", "sess-1"))
                .isInstanceOf(ConflictException.class);

        verify(auctionRepositoryPort, never()).save(any());
    }

    @Test
    void confirm_rejectsCallerWhoIsNotTheWinner() {
        Auction auction = endedAuctionWonBy("winner-1");
        when(auctionRepositoryPort.findByIdForUpdate("auction-1")).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> useCase.confirm("auction-1", "someone-else", "sess-1"))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(paymentGatewayPort);
    }

    @Test
    void confirm_rejectsUnknownAuction() {
        when(auctionRepositoryPort.findByIdForUpdate("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.confirm("ghost", "winner-1", "sess-1"))
                .isInstanceOf(AuctionNotFoundException.class);
    }
}
