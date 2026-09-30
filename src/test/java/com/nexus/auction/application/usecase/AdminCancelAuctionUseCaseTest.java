package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import com.nexus.common.core.exception.ConflictException;
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

class AdminCancelAuctionUseCaseTest {

    private AuctionRepositoryPort auctionRepositoryPort;
    private EventPublisherPort eventPublisherPort;
    private AdminCancelAuctionUseCase useCase;

    @BeforeEach
    void setUp() {
        auctionRepositoryPort = mock(AuctionRepositoryPort.class);
        eventPublisherPort = mock(EventPublisherPort.class);
        useCase = new AdminCancelAuctionUseCase(auctionRepositoryPort, eventPublisherPort);
        when(auctionRepositoryPort.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void cancel_cancelsActiveAuctionWithBidsRegardlessOfOwnership() {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Auction auction = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                        start, start.plus(2, ChronoUnit.HOURS))
                .withStatus(AuctionStatus.ACTIVE).withBid("bidder-1", new BigDecimal("100.00"));
        when(auctionRepositoryPort.findById(auction.getId())).thenReturn(Optional.of(auction));

        AuctionResult result = useCase.cancel(auction.getId(), "admin-1");

        assertThat(result.status()).isEqualTo("CANCELLED");
        verify(eventPublisherPort).publish(any());
    }

    @Test
    void cancel_rejectsWhenAlreadyEnded() {
        Instant start = Instant.now().minus(3, ChronoUnit.HOURS);
        Auction ended = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                        start, start.plus(1, ChronoUnit.HOURS))
                .withStatus(AuctionStatus.ENDED);
        when(auctionRepositoryPort.findById(ended.getId())).thenReturn(Optional.of(ended));

        assertThatThrownBy(() -> useCase.cancel(ended.getId(), "admin-1"))
                .isInstanceOf(ConflictException.class);
    }
}
