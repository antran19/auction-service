package com.nexus.auction.application.usecase;

import com.nexus.auction.application.exception.AuctionNotFoundException;
import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.AutoBidRepositoryPort;
import com.nexus.auction.application.port.out.BidBroadcastPort;
import com.nexus.auction.application.port.out.BidRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import com.nexus.auction.domain.model.AutoBid;
import com.nexus.auction.domain.model.Bid;
import com.nexus.common.core.exception.ConflictException;
import com.nexus.common.core.exception.ForbiddenException;
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
    private BidBroadcastPort bidBroadcastPort;
    private AutoBidRepositoryPort autoBidRepositoryPort;
    private PlaceBidUseCase useCase;

    @BeforeEach
    void setUp() {
        auctionRepositoryPort = mock(AuctionRepositoryPort.class);
        bidRepositoryPort = mock(BidRepositoryPort.class);
        eventPublisherPort = mock(EventPublisherPort.class);
        bidBroadcastPort = mock(BidBroadcastPort.class);
        autoBidRepositoryPort = mock(AutoBidRepositoryPort.class);
        useCase = new PlaceBidUseCase(auctionRepositoryPort, bidRepositoryPort, eventPublisherPort, bidBroadcastPort,
                autoBidRepositoryPort);
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

        BidResult result = useCase.placeBid(auction.getId(), "bidder-1", new BigDecimal("100.00"), "TRUSTED");

        assertThat(result.amount()).isEqualByComparingTo("100.00");
        verify(auctionRepositoryPort).save(argThat(a -> a.getCurrentHighestBidderId().equals("bidder-1")));
        verify(eventPublisherPort).publish(any(BidPlacedEvent.class));
    }

    @Test
    void placeBid_answersWithAStandingMaximum_andRecordsBothBids() {
        Auction auction = activeAuction();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));
        when(autoBidRepositoryPort.findByAuctionId(auction.getId()))
                .thenReturn(java.util.List.of(AutoBid.create(auction.getId(), "an", new BigDecimal("500.00"))));

        useCase.placeBid(auction.getId(), "binh", new BigDecimal("150.00"), "TRUSTED");

        // Binh's own bid, then the system's bid for An: one increment above.
        verify(bidRepositoryPort, times(2)).save(any(Bid.class));
        verify(bidRepositoryPort).save(argThat(b -> b.getBidderId().equals("an") && b.getAmount().compareTo(new BigDecimal("160")) == 0));
        verify(auctionRepositoryPort).save(argThat(a -> "an".equals(a.getCurrentHighestBidderId())
                && a.getCurrentHighestBid().compareTo(new BigDecimal("160")) == 0));
    }

    @Test
    void placeBid_sendsOneBidPlacedForTheBidThePersonSent_andOneOutbidForWhoEndsBehind() {
        Auction auction = activeAuction();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));
        when(autoBidRepositoryPort.findByAuctionId(auction.getId()))
                .thenReturn(java.util.List.of(AutoBid.create(auction.getId(), "an", new BigDecimal("500.00"))));

        useCase.placeBid(auction.getId(), "binh", new BigDecimal("150.00"), "TRUSTED");

        verify(eventPublisherPort, times(1)).publish(any(BidPlacedEvent.class));
        verify(eventPublisherPort).publish(argThat(e -> e instanceof OutbidEvent o && o.getOutbidBidderId().equals("binh")
                && o.getNewHighestBid().compareTo(new BigDecimal("160")) == 0));
        verify(eventPublisherPort, times(1)).publish(any(OutbidEvent.class));
    }

    @Test
    void placeBid_announcesEveryBidToTheWatchers_includingTheOnesTheSystemPlaced() {
        Auction auction = activeAuction();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));
        when(autoBidRepositoryPort.findByAuctionId(auction.getId()))
                .thenReturn(java.util.List.of(AutoBid.create(auction.getId(), "an", new BigDecimal("500.00"))));

        useCase.placeBid(auction.getId(), "binh", new BigDecimal("150.00"), "TRUSTED");

        verify(bidBroadcastPort, times(2)).bidPlaced(any(BidResult.class));
    }

    @Test
    void placeBid_aStandingMaximumTooLowToAnswer_changesNothing() {
        Auction auction = activeAuction();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));
        when(autoBidRepositoryPort.findByAuctionId(auction.getId()))
                .thenReturn(java.util.List.of(AutoBid.create(auction.getId(), "an", new BigDecimal("155.00"))));

        useCase.placeBid(auction.getId(), "binh", new BigDecimal("150.00"), "TRUSTED");

        verify(bidRepositoryPort, times(1)).save(any(Bid.class));
        verify(auctionRepositoryPort).save(argThat(a -> "binh".equals(a.getCurrentHighestBidderId())));
        verify(eventPublisherPort, never()).publish(any(OutbidEvent.class));
    }

    @Test
    void placeBid_theBidsOfTheSystemNeverExtendTheAuction() {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Auction nearlyOver = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                start, Instant.now().plus(2, ChronoUnit.MINUTES)).withStatus(AuctionStatus.ACTIVE);
        when(auctionRepositoryPort.findByIdForUpdate(nearlyOver.getId())).thenReturn(Optional.of(nearlyOver));
        when(autoBidRepositoryPort.findByAuctionId(nearlyOver.getId()))
                .thenReturn(java.util.List.of(AutoBid.create(nearlyOver.getId(), "an", new BigDecimal("500.00"))));

        useCase.placeBid(nearlyOver.getId(), "binh", new BigDecimal("150.00"), "TRUSTED");

        // The person's bid extended it once; the system's answer did not extend it a second time.
        verify(auctionRepositoryPort).save(argThat(a -> a.getExtensionCount() == 1));
    }

    @Test
    void placeBid_announcesTheAcceptedBidToTheWatchers() {
        Auction auction = activeAuction();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        BidResult result = useCase.placeBid(auction.getId(), "bidder-1", new BigDecimal("100.00"), "TRUSTED");

        verify(bidBroadcastPort).bidPlaced(result);
    }

    @Test
    void placeBid_announcesNothingWhenTheBidIsRefused() {
        Auction auction = activeAuction();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> useCase.placeBid(auction.getId(), "bidder-1", new BigDecimal("50.00"), "TRUSTED"))
                .isInstanceOf(ValidationException.class);

        verifyNoInteractions(bidBroadcastPort);
    }

    @Test
    void placeBid_publishesOutbidEventForThePreviousHighestBidder() {
        Auction auction = activeAuction().withBid("bidder-1", new BigDecimal("100.00"));
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        useCase.placeBid(auction.getId(), "bidder-2", new BigDecimal("110.00"), "TRUSTED");

        verify(eventPublisherPort).publish(argThat(event ->
                event instanceof OutbidEvent outbid && outbid.getOutbidBidderId().equals("bidder-1")));
    }

    @Test
    void placeBid_doesNotPublishOutbidWhenThereWasNoPreviousBidder() {
        Auction auction = activeAuction();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        useCase.placeBid(auction.getId(), "bidder-1", new BigDecimal("100.00"), "TRUSTED");

        verify(eventPublisherPort, never()).publish(any(OutbidEvent.class));
    }

    @Test
    void placeBid_rejectsBidBelowMinimum() {
        Auction auction = activeAuction();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> useCase.placeBid(auction.getId(), "bidder-1", new BigDecimal("50.00"), "TRUSTED"))
                .isInstanceOf(ValidationException.class);
        verify(bidRepositoryPort, never()).save(any());
    }

    @Test
    void placeBid_rejectsBidOnPendingAuction() {
        Auction pending = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                Instant.now().plus(1, ChronoUnit.HOURS), Instant.now().plus(2, ChronoUnit.HOURS));
        when(auctionRepositoryPort.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> useCase.placeBid(pending.getId(), "bidder-1", new BigDecimal("100.00"), "TRUSTED"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void placeBid_throwsNotFoundForUnknownAuction() {
        when(auctionRepositoryPort.findByIdForUpdate("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.placeBid("missing", "bidder-1", new BigDecimal("100.00"), "TRUSTED"))
                .isInstanceOf(AuctionNotFoundException.class);
    }

    // --- Fix-pass addition (2026-09-30 final review): AntiSnipingPolicy was only tested
    // in isolation (AntiSnipingPolicyTest) — nothing proved PlaceBidUseCase actually calls
    // it and applies the result. Removing the tryExtend call from PlaceBidUseCase would
    // have broken no existing test. ---

    @Test
    void placeBid_extendsEndTimeWhenBidArrivesWithinTheAntiSnipingWindow() {
        Instant now = Instant.now();
        Auction auction = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                        now.minus(1, ChronoUnit.HOURS), now.plus(3, ChronoUnit.MINUTES))
                .withStatus(AuctionStatus.ACTIVE);
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        useCase.placeBid(auction.getId(), "bidder-1", new BigDecimal("100.00"), "TRUSTED");

        verify(auctionRepositoryPort).save(argThat(a ->
                a.getExtensionCount() == 1 && a.getEndTime().isAfter(auction.getEndTime())));
    }

    @Test
    void placeBid_doesNotExtendEndTimeWhenBidArrivesOutsideTheAntiSnipingWindow() {
        Instant now = Instant.now();
        Auction auction = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                        now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS))
                .withStatus(AuctionStatus.ACTIVE);
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        useCase.placeBid(auction.getId(), "bidder-1", new BigDecimal("100.00"), "TRUSTED");

        verify(auctionRepositoryPort).save(argThat(a ->
                a.getExtensionCount() == 0 && a.getEndTime().equals(auction.getEndTime())));
    }

    @Test
    void placeBid_rejectsBidderBelowMinReputationToBid() {
        Auction auction = activeAuction();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> useCase.placeBid(auction.getId(), "bidder-1", new BigDecimal("100.00"), "LOW"))
                .isInstanceOf(ForbiddenException.class);
        verify(bidRepositoryPort, never()).save(any());
    }

    @Test
    void placeBid_allowsNormalTrustLevelToBid() {
        Auction auction = activeAuction();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        BidResult result = useCase.placeBid(auction.getId(), "bidder-1", new BigDecimal("100.00"), "NORMAL");

        assertThat(result.amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void placeBid_allowsBiddingWhenTrustLevelIsMissing() {
        // A null trustLevel (old token predating the claim) fails open rather than blocking
        // every bidder.
        Auction auction = activeAuction();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        BidResult result = useCase.placeBid(auction.getId(), "bidder-1", new BigDecimal("100.00"), null);

        assertThat(result.amount()).isEqualByComparingTo("100.00");
    }
}
