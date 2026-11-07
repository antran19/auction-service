package com.nexus.auction.application.usecase;

import com.nexus.auction.application.exception.AuctionNotFoundException;
import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.AutoBidRepositoryPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AutoBid;
import com.nexus.common.core.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetMyAutoBidUseCaseTest {

    private AuctionRepositoryPort auctionRepositoryPort;
    private AutoBidRepositoryPort autoBidRepositoryPort;
    private GetMyAutoBidUseCase useCase;

    @BeforeEach
    void setUp() {
        auctionRepositoryPort = mock(AuctionRepositoryPort.class);
        autoBidRepositoryPort = mock(AutoBidRepositoryPort.class);
        useCase = new GetMyAutoBidUseCase(auctionRepositoryPort, autoBidRepositoryPort);
    }

    private Auction anAuction() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Auction auction = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                start, start.plus(2, ChronoUnit.HOURS));
        when(auctionRepositoryPort.findById(auction.getId())).thenReturn(Optional.of(auction));
        return auction;
    }

    @Test
    void returnsTheMaximumTheBidderSet() {
        Auction auction = anAuction();
        AutoBid autoBid = AutoBid.create(auction.getId(), "bidder-1", new BigDecimal("500.00"));
        when(autoBidRepositoryPort.findByAuctionIdAndBidderId(auction.getId(), "bidder-1")).thenReturn(Optional.of(autoBid));

        AutoBidResult result = useCase.get(auction.getId(), "bidder-1");

        assertThat(result.maxAmount()).isEqualByComparingTo("500.00");
        assertThat(result.bidderId()).isEqualTo("bidder-1");
        assertThat(result.auctionId()).isEqualTo(auction.getId());
    }

    @Test
    void aBidderWithoutAMaximum_gets404WithItsOwnCode() {
        Auction auction = anAuction();
        when(autoBidRepositoryPort.findByAuctionIdAndBidderId(auction.getId(), "bidder-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(auction.getId(), "bidder-1"))
                .isInstanceOfSatisfying(NotFoundException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("AUTO_BID_NOT_FOUND"));
    }

    @Test
    void anUnknownAuction_isAuctionNotFound_notAutoBidNotFound() {
        when(auctionRepositoryPort.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get("missing", "bidder-1")).isInstanceOf(AuctionNotFoundException.class);
    }

    @Test
    void theLookupIsAlwaysByTheBidderGiven_soAnotherPersonsMaximumIsNeverReturned() {
        Auction auction = anAuction();
        when(autoBidRepositoryPort.findByAuctionIdAndBidderId(auction.getId(), "someone-else"))
                .thenReturn(Optional.of(AutoBid.create(auction.getId(), "someone-else", new BigDecimal("900.00"))));
        when(autoBidRepositoryPort.findByAuctionIdAndBidderId(auction.getId(), "bidder-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(auction.getId(), "bidder-1")).isInstanceOf(NotFoundException.class);
    }
}
