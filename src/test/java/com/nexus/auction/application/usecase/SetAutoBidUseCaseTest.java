package com.nexus.auction.application.usecase;

import com.nexus.auction.application.exception.AuctionNotFoundException;
import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.AutoBidRepositoryPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import com.nexus.auction.domain.model.AutoBid;
import com.nexus.common.core.exception.ConflictException;
import com.nexus.common.core.exception.ForbiddenException;
import com.nexus.common.core.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SetAutoBidUseCaseTest {

    private AuctionRepositoryPort auctionRepositoryPort;
    private AutoBidRepositoryPort autoBidRepositoryPort;
    private SetAutoBidUseCase useCase;

    @BeforeEach
    void setUp() {
        auctionRepositoryPort = mock(AuctionRepositoryPort.class);
        autoBidRepositoryPort = mock(AutoBidRepositoryPort.class);
        useCase = new SetAutoBidUseCase(auctionRepositoryPort, autoBidRepositoryPort);
        when(autoBidRepositoryPort.save(any(AutoBid.class))).thenAnswer(inv -> inv.getArgument(0));
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
