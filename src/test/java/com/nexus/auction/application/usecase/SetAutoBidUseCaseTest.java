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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SetAutoBidUseCaseTest {

    private AuctionRepositoryPort auctionRepositoryPort;
    private AutoBidRepositoryPort autoBidRepositoryPort;
    private BidRepositoryPort bidRepositoryPort;
    private BidBroadcastPort bidBroadcastPort;
    private EventPublisherPort eventPublisherPort;
    private SetAutoBidUseCase useCase;

    @BeforeEach
    void setUp() {
        auctionRepositoryPort = mock(AuctionRepositoryPort.class);
        autoBidRepositoryPort = mock(AutoBidRepositoryPort.class);
        bidRepositoryPort = mock(BidRepositoryPort.class);
        bidBroadcastPort = mock(BidBroadcastPort.class);
        eventPublisherPort = mock(EventPublisherPort.class);
        useCase = new SetAutoBidUseCase(auctionRepositoryPort, autoBidRepositoryPort, bidRepositoryPort,
                bidBroadcastPort, eventPublisherPort);
        when(autoBidRepositoryPort.save(any(AutoBid.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bidRepositoryPort.save(any(Bid.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auctionRepositoryPort.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(autoBidRepositoryPort.findByAuctionIdAndBidderId(any(), any())).thenReturn(Optional.empty());
    }

    // Starting price 100, increment 10, seller "seller-1".
    private Auction activeAuction() {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Auction auction = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                        start, start.plus(2, ChronoUnit.HOURS))
                .withStatus(AuctionStatus.ACTIVE);
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));
        return auction;
    }

    // ---- the new maximum places bids straight away

    @Test
    void aFirstMaximumOnAnAuctionWithNoBid_placesTheStartingPriceBid() {
        Auction auction = activeAuction();
        when(autoBidRepositoryPort.findByAuctionId(auction.getId())).thenAnswer(inv ->
                java.util.List.of(AutoBid.create(auction.getId(), "an", new BigDecimal("500.00"))));

        useCase.setAutoBid(auction.getId(), "an", new BigDecimal("500.00"), "TRUSTED");

        verify(bidRepositoryPort).save(argThat(b -> b.getBidderId().equals("an") && b.getAmount().compareTo(new BigDecimal("100")) == 0));
        verify(auctionRepositoryPort).save(argThat(a -> "an".equals(a.getCurrentHighestBidderId())
                && a.getCurrentHighestBid().compareTo(new BigDecimal("100")) == 0));
        verify(eventPublisherPort, never()).publish(any());
    }

    @Test
    void aMaximumThatBeatsTheLeader_takesTheLead_andTheFormerLeaderGetsOneOutbid() {
        Auction auction = activeAuction().withBid("binh", new BigDecimal("150.00"));
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));
        AutoBid chi = AutoBid.create(auction.getId(), "chi", new BigDecimal("500.00"));
        when(autoBidRepositoryPort.findByAuctionId(auction.getId())).thenReturn(java.util.List.of(chi));

        useCase.setAutoBid(auction.getId(), "chi", new BigDecimal("500.00"), "TRUSTED");

        verify(bidRepositoryPort).save(argThat(b -> b.getBidderId().equals("chi") && b.getAmount().compareTo(new BigDecimal("160")) == 0));
        verify(auctionRepositoryPort).save(argThat(a -> "chi".equals(a.getCurrentHighestBidderId())));
        verify(eventPublisherPort).publish(argThat(e -> e instanceof OutbidEvent o && o.getOutbidBidderId().equals("binh")
                && o.getNewHighestBid().compareTo(new BigDecimal("160")) == 0));
        verify(eventPublisherPort, times(1)).publish(any(OutbidEvent.class));
    }

    @Test
    void aMaximumThatDoesNotChangeThePrice_placesNothingAndSavesNoAuction() {
        // The leader raises their own maximum: nobody else can bid, so the price stays.
        Auction auction = activeAuction().withBid("an", new BigDecimal("100.00"));
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));
        when(autoBidRepositoryPort.findByAuctionId(auction.getId())).thenAnswer(inv ->
                java.util.List.of(AutoBid.create(auction.getId(), "an", new BigDecimal("800.00"))));

        useCase.setAutoBid(auction.getId(), "an", new BigDecimal("800.00"), "TRUSTED");

        verify(bidRepositoryPort, never()).save(any(Bid.class));
        verify(auctionRepositoryPort, never()).save(any(Auction.class));
        verify(eventPublisherPort, never()).publish(any());
    }

    @Test
    void firstMaximum_isStored() {
        Auction auction = activeAuction();

        AutoBidResult result = useCase.setAutoBid(auction.getId(), "bidder-1", new BigDecimal("500.00"), "TRUSTED");

        assertThat(result.maxAmount()).isEqualByComparingTo("500.00");
        assertThat(result.bidderId()).isEqualTo("bidder-1");
        verify(autoBidRepositoryPort).save(any(AutoBid.class));
    }

    @Test
    void aMaximumBelowTheMinimumBid_isRefusedAndNothingIsStored() {
        Auction auction = activeAuction();

        assertThatThrownBy(() -> useCase.setAutoBid(auction.getId(), "bidder-1", new BigDecimal("99.00"), "TRUSTED"))
                .isInstanceOf(ValidationException.class);
        verify(autoBidRepositoryPort, never()).save(any());
    }

    @Test
    void theSeller_cannotSetAMaximumOnTheirOwnAuction() {
        Auction auction = activeAuction();

        assertThatThrownBy(() -> useCase.setAutoBid(auction.getId(), "seller-1", new BigDecimal("500.00"), "TRUSTED"))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("SELLER_CANNOT_BID_OWN_AUCTION"));
        verify(autoBidRepositoryPort, never()).save(any());
    }

    @Test
    void anAuctionThatIsNotActive_refusesAMaximum() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Auction pending = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                start, start.plus(2, ChronoUnit.HOURS));
        when(auctionRepositoryPort.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> useCase.setAutoBid(pending.getId(), "bidder-1", new BigDecimal("500.00"), "TRUSTED"))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("AUCTION_NOT_ACTIVE"));
    }

    @Test
    void anUnknownAuction_isNotFound() {
        when(auctionRepositoryPort.findByIdForUpdate("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.setAutoBid("missing", "bidder-1", new BigDecimal("500.00"), "TRUSTED"))
                .isInstanceOf(AuctionNotFoundException.class);
    }

    @Test
    void aBidderWithLowTrust_isRefused() {
        Auction auction = activeAuction();

        assertThatThrownBy(() -> useCase.setAutoBid(auction.getId(), "bidder-1", new BigDecimal("500.00"), "LOW"))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("INSUFFICIENT_TRUST_TO_BID"));
        verify(autoBidRepositoryPort, never()).save(any());
    }

    @Test
    void raisingTheMaximum_updatesTheSameAutoBid() {
        Auction auction = activeAuction();
        AutoBid existing = AutoBid.create(auction.getId(), "bidder-1", new BigDecimal("500.00"));
        when(autoBidRepositoryPort.findByAuctionIdAndBidderId(auction.getId(), "bidder-1")).thenReturn(Optional.of(existing));

        AutoBidResult result = useCase.setAutoBid(auction.getId(), "bidder-1", new BigDecimal("800.00"), "TRUSTED");

        assertThat(result.id()).isEqualTo(existing.getId());
        assertThat(result.maxAmount()).isEqualByComparingTo("800.00");
        verify(autoBidRepositoryPort).save(org.mockito.ArgumentMatchers.argThat(
                a -> a.getId().equals(existing.getId()) && a.getCreatedAt().equals(existing.getCreatedAt())));
    }

    @Test
    void loweringTheMaximum_isRefused_andTheOldValueIsKept() {
        Auction auction = activeAuction();
        AutoBid existing = AutoBid.create(auction.getId(), "bidder-1", new BigDecimal("500.00"));
        when(autoBidRepositoryPort.findByAuctionIdAndBidderId(auction.getId(), "bidder-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.setAutoBid(auction.getId(), "bidder-1", new BigDecimal("300.00"), "TRUSTED"))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("AUTO_BID_CANNOT_BE_LOWERED"));
        verify(autoBidRepositoryPort, never()).save(any());
    }

    @Test
    void sendingTheSameMaximumAgain_changesNothing() {
        Auction auction = activeAuction();
        AutoBid existing = AutoBid.create(auction.getId(), "bidder-1", new BigDecimal("500.00"));
        when(autoBidRepositoryPort.findByAuctionIdAndBidderId(auction.getId(), "bidder-1")).thenReturn(Optional.of(existing));

        AutoBidResult result = useCase.setAutoBid(auction.getId(), "bidder-1", new BigDecimal("500.00"), "TRUSTED");

        assertThat(result.id()).isEqualTo(existing.getId());
        verify(autoBidRepositoryPort, never()).save(any());
    }
}
