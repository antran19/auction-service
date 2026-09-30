package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
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

class CancelAuctionUseCaseTest {

    private AuctionRepositoryPort auctionRepositoryPort;
    private EventPublisherPort eventPublisherPort;
    private CancelAuctionUseCase useCase;

    @BeforeEach
    void setUp() {
        auctionRepositoryPort = mock(AuctionRepositoryPort.class);
        eventPublisherPort = mock(EventPublisherPort.class);
        useCase = new CancelAuctionUseCase(auctionRepositoryPort, eventPublisherPort);
        when(auctionRepositoryPort.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));
        // Poison pill: cancel() must use the locked read (findByIdForUpdate), never the
        // unlocked one, or a concurrent bid/settlement could be silently overwritten.
        when(auctionRepositoryPort.findById(any())).thenThrow(new AssertionError(
                "CancelAuctionUseCase must call findByIdForUpdate, not findById"));
    }

    private Auction newAuction() {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        return Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                start, start.plus(2, ChronoUnit.HOURS));
    }

    @Test
    void cancel_allowsSellerToCancelWhilePending() {
        Auction auction = newAuction();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        AuctionResult result = useCase.cancel(auction.getId(), "seller-1");

        assertThat(result.status()).isEqualTo("CANCELLED");
        verify(eventPublisherPort).publish(any());
    }

    @Test
    void cancel_allowsSellerToCancelWhileActiveWithZeroBids() {
        Auction auction = newAuction().withStatus(AuctionStatus.ACTIVE);
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        AuctionResult result = useCase.cancel(auction.getId(), "seller-1");

        assertThat(result.status()).isEqualTo("CANCELLED");
    }

    @Test
    void cancel_rejectsSellerOnceAuctionHasABid() {
        Auction auction = newAuction().withStatus(AuctionStatus.ACTIVE).withBid("bidder-1", new BigDecimal("100.00"));
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> useCase.cancel(auction.getId(), "seller-1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cancel_rejectsNonOwner() {
        Auction auction = newAuction();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> useCase.cancel(auction.getId(), "other-seller"))
                .isInstanceOf(ForbiddenException.class);
    }
}
