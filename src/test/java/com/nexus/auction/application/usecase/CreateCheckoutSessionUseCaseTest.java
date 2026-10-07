package com.nexus.auction.application.usecase;

import com.nexus.auction.application.exception.AuctionNotFoundException;
import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.PaymentGatewayPort;
import com.nexus.auction.domain.model.Auction;
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

class CreateCheckoutSessionUseCaseTest {

    private AuctionRepositoryPort auctionRepositoryPort;
    private PaymentGatewayPort paymentGatewayPort;
    private CreateCheckoutSessionUseCase useCase;

    @BeforeEach
    void setUp() {
        auctionRepositoryPort = mock(AuctionRepositoryPort.class);
        paymentGatewayPort = mock(PaymentGatewayPort.class);
        useCase = new CreateCheckoutSessionUseCase(auctionRepositoryPort, paymentGatewayPort);
    }

    private Auction endedAuctionWonBy(String winnerId) {
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS);
        Auction pending = Auction.reconstitute("auction-1", "product-1", "seller-1",
                new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("150.00"), winnerId,
                com.nexus.auction.domain.model.AuctionStatus.PENDING, start, start.plus(1, ChronoUnit.HOURS),
                0, null, null, null, false, null, start, start);
        return pending.withSettlement(winnerId, new BigDecimal("150.00"), Instant.now().plus(24, ChronoUnit.HOURS));
    }

    @Test
    void createSession_returnsCheckoutUrlForTheWinner() {
        Auction auction = endedAuctionWonBy("winner-1");
        when(auctionRepositoryPort.findById("auction-1")).thenReturn(Optional.of(auction));
        when(paymentGatewayPort.createCheckoutSession("auction-1", "winner-1", new BigDecimal("150.00"),
                "https://fe/success", "https://fe/cancel"))
                .thenReturn(new PaymentGatewayPort.CheckoutSession("sess-1", "https://checkout.stripe.com/sess-1"));

        var result = useCase.createSession("auction-1", "winner-1", "https://fe/success", "https://fe/cancel");

        assertThat(result.checkoutUrl()).isEqualTo("https://checkout.stripe.com/sess-1");
    }

    @Test
    void createSession_rejectsUnknownAuction() {
        when(auctionRepositoryPort.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.createSession("ghost", "winner-1", "s", "c"))
                .isInstanceOf(AuctionNotFoundException.class);
    }

    @Test
    void createSession_rejectsCallerWhoIsNotTheWinner() {
        Auction auction = endedAuctionWonBy("winner-1");
        when(auctionRepositoryPort.findById("auction-1")).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> useCase.createSession("auction-1", "someone-else", "s", "c"))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(paymentGatewayPort);
    }

    @Test
    void createSession_rejectsAuctionThatHasNotEnded() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Auction active = Auction.reconstitute("auction-1", "product-1", "seller-1",
                new BigDecimal("100.00"), new BigDecimal("10.00"), null, null,
                com.nexus.auction.domain.model.AuctionStatus.ACTIVE, start, start.plus(1, ChronoUnit.HOURS),
                0, null, null, null, false, null, start, start);
        when(auctionRepositoryPort.findById("auction-1")).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> useCase.createSession("auction-1", "winner-1", "s", "c"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createSession_rejectsAlreadyPaidAuction() {
        Auction paid = endedAuctionWonBy("winner-1").markPaid(Instant.now());
        when(auctionRepositoryPort.findById("auction-1")).thenReturn(Optional.of(paid));

        assertThatThrownBy(() -> useCase.createSession("auction-1", "winner-1", "s", "c"))
                .isInstanceOf(ConflictException.class);

        verifyNoInteractions(paymentGatewayPort);
    }
}
