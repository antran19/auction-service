package com.nexus.auction.application.usecase;

import com.nexus.auction.application.exception.AuctionNotFoundException;
import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.BidRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import com.nexus.auction.domain.model.Bid;
import com.nexus.common.core.exception.ConflictException;
import com.nexus.common.core.exception.ValidationException;
import com.nexus.common.events.BidPlacedEvent;
import com.nexus.common.events.OutbidEvent;
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

class PlaceBidUseCaseTest {

    private AuctionRepositoryPort auctionRepositoryPort;
    private BidRepositoryPort bidRepositoryPort;
    private EventPublisherPort eventPublisherPort;
    private PlaceBidUseCase useCase;

    @BeforeEach
    void setUp() {
        auctionRepositoryPort = mock(AuctionRepositoryPort.class);
        bidRepositoryPort = mock(BidRepositoryPort.class);
        eventPublisherPort = mock(EventPublisherPort.class);
        useCase = new PlaceBidUseCase(auctionRepositoryPort, bidRepositoryPort, eventPublisherPort);
        when(auctionRepositoryPort.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bidRepositoryPort.save(any(Bid.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Auction activeAuction() {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        return Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                        start, start.plus(2, ChronoUnit.HOURS))
                .withStatus(AuctionStatus.ACTIVE);
    }

    @Test
    void placeBid_savesBidAndUpdatesAuctionHighest() {
        Auction auction = activeAuction();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        BidResult result = useCase.placeBid(auction.getId(), "bidder-1", new BigDecimal("100.00"));

        assertThat(result.amount()).isEqualByComparingTo("100.00");
        verify(auctionRepositoryPort).save(argThat(a -> a.getCurrentHighestBidderId().equals("bidder-1")));
        verify(eventPublisherPort).publish(any(BidPlacedEvent.class));
    }

    @Test
    void placeBid_publishesOutbidEventForThePreviousHighestBidder() {
        Auction auction = activeAuction().withBid("bidder-1", new BigDecimal("100.00"));
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        useCase.placeBid(auction.getId(), "bidder-2", new BigDecimal("110.00"));

        verify(eventPublisherPort).publish(argThat(event ->
                event instanceof OutbidEvent outbid && outbid.getOutbidBidderId().equals("bidder-1")));
    }

    @Test
    void placeBid_doesNotPublishOutbidWhenThereWasNoPreviousBidder() {
        Auction auction = activeAuction();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        useCase.placeBid(auction.getId(), "bidder-1", new BigDecimal("100.00"));

        verify(eventPublisherPort, never()).publish(any(OutbidEvent.class));
    }

    @Test
    void placeBid_rejectsBidBelowMinimum() {
        Auction auction = activeAuction();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> useCase.placeBid(auction.getId(), "bidder-1", new BigDecimal("50.00")))
                .isInstanceOf(ValidationException.class);
        verify(bidRepositoryPort, never()).save(any());
    }

    @Test
    void placeBid_rejectsBidOnPendingAuction() {
        Auction pending = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                Instant.now().plus(1, ChronoUnit.HOURS), Instant.now().plus(2, ChronoUnit.HOURS));
        when(auctionRepositoryPort.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> useCase.placeBid(pending.getId(), "bidder-1", new BigDecimal("100.00")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void placeBid_throwsNotFoundForUnknownAuction() {
        when(auctionRepositoryPort.findByIdForUpdate("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.placeBid("missing", "bidder-1", new BigDecimal("100.00")))
                .isInstanceOf(AuctionNotFoundException.class);
    }
}
