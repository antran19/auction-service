package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionBidStats;
import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.BidRepositoryPort;
import com.nexus.auction.application.port.out.ProductSnapshotRepositoryPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListMySellingUseCaseTest {

    private AuctionRepositoryPort auctionRepositoryPort;
    private BidRepositoryPort bidRepositoryPort;
    private ProductSnapshotRepositoryPort productSnapshotRepositoryPort;
    private ListMySellingUseCase useCase;

    @BeforeEach
    void setUp() {
        auctionRepositoryPort = mock(AuctionRepositoryPort.class);
        bidRepositoryPort = mock(BidRepositoryPort.class);
        productSnapshotRepositoryPort = mock(ProductSnapshotRepositoryPort.class);
        useCase = new ListMySellingUseCase(auctionRepositoryPort, bidRepositoryPort, productSnapshotRepositoryPort);
        when(bidRepositoryPort.statsByAuctionIds(anyList())).thenReturn(List.of());
        when(productSnapshotRepositoryPort.findNamesByIds(anyCollection())).thenReturn(Map.of());
    }

    private static Auction auction(String productId) {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        return Auction.create(productId, "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                start, start.plus(2, ChronoUnit.HOURS)).withStatus(AuctionStatus.ACTIVE);
    }

    @Test
    void listsTheSellersAuctions_withBidAndBidderCounts_andTheProductName() {
        Auction a = auction("product-1");
        when(auctionRepositoryPort.search(null, "seller-1", null, 0, 20)).thenReturn(List.of(a));
        when(bidRepositoryPort.statsByAuctionIds(List.of(a.getId())))
                .thenReturn(List.of(new AuctionBidStats(a.getId(), 7, 3)));
        when(productSnapshotRepositoryPort.findNamesByIds(anyCollection())).thenReturn(Map.of("product-1", "Watch"));

        List<MySellingItem> items = useCase.list("seller-1", null, 0, 20);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.auctionId()).isEqualTo(a.getId());
            assertThat(item.productName()).isEqualTo("Watch");
            assertThat(item.bidCount()).isEqualTo(7);
            assertThat(item.bidderCount()).isEqualTo(3);
            assertThat(item.status()).isEqualTo("ACTIVE");
        });
    }

    @Test
    void anAuctionWithoutBids_hasZeroCounts_andAnUnnamedProductHasNoName() {
        Auction a = auction("product-1");
        when(auctionRepositoryPort.search(any(), eq("seller-1"), any(), anyInt(), anyInt())).thenReturn(List.of(a));

        MySellingItem item = useCase.list("seller-1", null, 0, 20).get(0);

        assertThat(item.bidCount()).isZero();
        assertThat(item.bidderCount()).isZero();
        assertThat(item.productName()).isNull();
    }

    @Test
    void theStatusFilterIsPassedOn_caseInsensitively_andBlankMeansAll() {
        when(auctionRepositoryPort.search(any(), anyString(), any(), anyInt(), anyInt())).thenReturn(List.of());

        useCase.list("seller-1", "ended", 0, 20);
        useCase.list("seller-1", "  ", 0, 20);

        verify(auctionRepositoryPort).search("ENDED", "seller-1", null, 0, 20);
        verify(auctionRepositoryPort).search(null, "seller-1", null, 0, 20);
    }

    @Test
    void aSellerWithNoAuctions_getsAnEmptyList_withoutLookingUpBidsOrNames() {
        when(auctionRepositoryPort.search(any(), anyString(), any(), anyInt(), anyInt())).thenReturn(List.of());

        assertThat(useCase.list("seller-1", null, 0, 20)).isEmpty();
        verify(bidRepositoryPort, never()).statsByAuctionIds(anyList());
        verify(productSnapshotRepositoryPort, never()).findNamesByIds(anyCollection());
    }

    @Test
    void aBadStatusPageOrSize_isRejectedBeforeAnythingIsRead() {
        assertThatThrownBy(() -> useCase.list("seller-1", "BOGUS", 0, 20)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> useCase.list("seller-1", null, -1, 20)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> useCase.list("seller-1", null, 0, 0)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> useCase.list("seller-1", null, 0, 101)).isInstanceOf(ValidationException.class);
        verify(auctionRepositoryPort, never()).search(any(), any(), any(), anyInt(), anyInt());
    }
}
