package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.AutoBidRepositoryPort;
import com.nexus.auction.application.port.out.BidRepositoryPort;
import com.nexus.auction.application.port.out.BidderAuctionSummary;
import com.nexus.auction.application.port.out.ProductSnapshotRepositoryPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import com.nexus.auction.domain.model.AutoBid;
import com.nexus.common.core.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListMyBiddingUseCaseTest {

    private BidRepositoryPort bidRepositoryPort;
    private AuctionRepositoryPort auctionRepositoryPort;
    private AutoBidRepositoryPort autoBidRepositoryPort;
    private ProductSnapshotRepositoryPort productSnapshotRepositoryPort;
    private ListMyBiddingUseCase useCase;

    @BeforeEach
    void setUp() {
        bidRepositoryPort = mock(BidRepositoryPort.class);
        auctionRepositoryPort = mock(AuctionRepositoryPort.class);
        autoBidRepositoryPort = mock(AutoBidRepositoryPort.class);
        productSnapshotRepositoryPort = mock(ProductSnapshotRepositoryPort.class);
        useCase = new ListMyBiddingUseCase(bidRepositoryPort, auctionRepositoryPort, autoBidRepositoryPort,
                productSnapshotRepositoryPort);
        when(productSnapshotRepositoryPort.findNamesByIds(anyCollection())).thenReturn(Map.of());
        when(autoBidRepositoryPort.findByBidderIdAndAuctionIds(anyString(), anyList())).thenReturn(List.of());
    }

    private static Auction auction(AuctionStatus status, String leader, String winner, String highest) {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Auction base = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                start, start.plus(2, ChronoUnit.HOURS)).withStatus(AuctionStatus.ACTIVE);
        Auction withBid = leader == null ? base : base.withBid(leader, new BigDecimal(highest));
        return switch (status) {
            case ACTIVE, PENDING -> withBid;
            case ENDED -> withBid.withStatus(AuctionStatus.ENDED).withSettlement(winner, new BigDecimal(highest),
                    Instant.now().plus(1, ChronoUnit.DAYS));
            case CANCELLED -> withBid.withStatus(AuctionStatus.CANCELLED);
        };
    }

    private void bidOn(String bidder, Auction... auctions) {
        List<BidderAuctionSummary> summaries = java.util.Arrays.stream(auctions)
                .map(a -> new BidderAuctionSummary(a.getId(), new BigDecimal("120.00"), Instant.now())).toList();
        when(bidRepositoryPort.findAuctionsBidOnBy(bidder, 0, 20)).thenReturn(summaries);
        when(auctionRepositoryPort.findAllByIds(anyList())).thenReturn(List.of(auctions));
    }

    @Test
    void anActiveAuctionWhereIAmTheHighestBidder_isLeading() {
        Auction a = auction(AuctionStatus.ACTIVE, "me", null, "130.00");
        bidOn("me", a);

        assertThat(useCase.list("me", 0, 20)).singleElement().satisfies(item -> {
            assertThat(item.myStatus()).isEqualTo("LEADING");
            assertThat(item.auctionStatus()).isEqualTo("ACTIVE");
            assertThat(item.currentHighestBid()).isEqualByComparingTo("130.00");
            assertThat(item.myHighestBid()).isEqualByComparingTo("120.00");
        });
    }

    @Test
    void anActiveAuctionWhereSomeoneBidMore_isOutbid() {
        bidOn("me", auction(AuctionStatus.ACTIVE, "someone", null, "140.00"));

        assertThat(useCase.list("me", 0, 20)).singleElement().extracting(MyBiddingItem::myStatus).isEqualTo("OUTBID");
    }

    @Test
    void anEndedAuctionIWon_isWon_andOneILost_isLost() {
        Auction won = auction(AuctionStatus.ENDED, "me", "me", "150.00");
        Auction lost = auction(AuctionStatus.ENDED, "someone", "someone", "160.00");
        bidOn("me", won, lost);

        assertThat(useCase.list("me", 0, 20)).extracting(MyBiddingItem::myStatus).containsExactly("WON", "LOST");
    }

    @Test
    void aCancelledAuction_isCancelled() {
        bidOn("me", auction(AuctionStatus.CANCELLED, "me", null, "130.00"));

        assertThat(useCase.list("me", 0, 20)).singleElement().extracting(MyBiddingItem::myStatus).isEqualTo("CANCELLED");
    }

    @Test
    void myMaximumIsShownWhereIHaveOne_andEmptyWhereIDoNot() {
        Auction withMax = auction(AuctionStatus.ACTIVE, "me", null, "130.00");
        Auction without = auction(AuctionStatus.ACTIVE, "someone", null, "130.00");
        bidOn("me", withMax, without);
        when(autoBidRepositoryPort.findByBidderIdAndAuctionIds(anyString(), anyList()))
                .thenReturn(List.of(AutoBid.create(withMax.getId(), "me", new BigDecimal("500.00"))));

        List<MyBiddingItem> items = useCase.list("me", 0, 20);

        assertThat(items.get(0).myMaxAutoBid()).isEqualByComparingTo("500.00");
        assertThat(items.get(1).myMaxAutoBid()).isNull();
    }

    @Test
    void theProductNameComesFromTheSnapshot_andIsNullWhereNotKnownYet() {
        Auction named = auction(AuctionStatus.ACTIVE, "me", null, "130.00");
        Auction unnamed = Auction.create("product-2", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.HOURS))
                .withStatus(AuctionStatus.ACTIVE).withBid("me", new BigDecimal("120.00"));
        bidOn("me", named, unnamed);
        when(productSnapshotRepositoryPort.findNamesByIds(anyCollection())).thenReturn(Map.of("product-1", "Watch"));

        List<MyBiddingItem> items = useCase.list("me", 0, 20);

        assertThat(items.get(0).productName()).isEqualTo("Watch");
        assertThat(items.get(1).productName()).isNull();
    }

    @Test
    void theNamesAreAskedForOncePerProduct_notOncePerAuction() {
        Auction a = auction(AuctionStatus.ACTIVE, "me", null, "130.00");
        Auction b = auction(AuctionStatus.ACTIVE, "someone", null, "140.00");
        bidOn("me", a, b);

        useCase.list("me", 0, 20);

        verify(productSnapshotRepositoryPort).findNamesByIds(List.of("product-1"));
    }

    @Test
    void theOrderOfTheSummariesIsKept_mostRecentFirst() {
        Auction first = auction(AuctionStatus.ACTIVE, "me", null, "130.00");
        Auction second = auction(AuctionStatus.ACTIVE, "me", null, "140.00");
        bidOn("me", first, second);
        // the auctions come back from the database in any order
        when(auctionRepositoryPort.findAllByIds(anyList())).thenReturn(List.of(second, first));

        assertThat(useCase.list("me", 0, 20)).extracting(MyBiddingItem::auctionId)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void aPersonWhoHasNotBid_getsAnEmptyList_withoutLookingUpAuctions() {
        when(bidRepositoryPort.findAuctionsBidOnBy("me", 0, 20)).thenReturn(List.of());

        assertThat(useCase.list("me", 0, 20)).isEmpty();
        verify(auctionRepositoryPort, never()).findAllByIds(anyList());
    }

    @Test
    void anAuctionThatNoLongerExists_isSkipped() {
        Auction gone = auction(AuctionStatus.ACTIVE, "me", null, "130.00");
        when(bidRepositoryPort.findAuctionsBidOnBy("me", 0, 20))
                .thenReturn(List.of(new BidderAuctionSummary(gone.getId(), new BigDecimal("120.00"), Instant.now())));
        when(auctionRepositoryPort.findAllByIds(anyList())).thenReturn(List.of());

        assertThat(useCase.list("me", 0, 20)).isEmpty();
    }

    @Test
    void aBadPageOrSize_isRejectedBeforeAnythingIsRead() {
        assertThatThrownBy(() -> useCase.list("me", -1, 20)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> useCase.list("me", 0, 0)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> useCase.list("me", 0, 101)).isInstanceOf(ValidationException.class);
        verify(bidRepositoryPort, never()).findAuctionsBidOnBy(anyString(), anyInt(), anyInt());
    }
}
