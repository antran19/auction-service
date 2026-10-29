package com.nexus.auction.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuctionStatusTest {

    // The whole lifecycle in one table: from -> the only statuses it may move to.
    private static final Map<AuctionStatus, Set<AuctionStatus>> ALLOWED = Map.of(
            AuctionStatus.PENDING, Set.of(AuctionStatus.ACTIVE, AuctionStatus.CANCELLED),
            AuctionStatus.ACTIVE, Set.of(AuctionStatus.ENDED, AuctionStatus.CANCELLED),
            AuctionStatus.ENDED, Set.of(),
            AuctionStatus.CANCELLED, Set.of());

    @ParameterizedTest
    @EnumSource(AuctionStatus.class)
    void everyPairOfStatuses_isAllowedExactlyWhenTheTableSaysSo(AuctionStatus from) {
        for (AuctionStatus to : AuctionStatus.values()) {
            assertThat(from.canTransitionTo(to))
                    .as("%s -> %s", from, to)
                    .isEqualTo(ALLOWED.get(from).contains(to));
        }
    }

    @Test
    void theNormalLifeIsPendingActiveEnded() {
        assertThat(AuctionStatus.PENDING.canTransitionTo(AuctionStatus.ACTIVE)).isTrue();
        assertThat(AuctionStatus.ACTIVE.canTransitionTo(AuctionStatus.ENDED)).isTrue();
    }

    @Test
    void anAuctionCannotSkipOrGoBack() {
        assertThat(AuctionStatus.PENDING.canTransitionTo(AuctionStatus.ENDED)).isFalse();
        assertThat(AuctionStatus.ACTIVE.canTransitionTo(AuctionStatus.PENDING)).isFalse();
        assertThat(AuctionStatus.ENDED.canTransitionTo(AuctionStatus.ACTIVE)).isFalse();
    }

    @Test
    void finalStatusesLeadNowhere() {
        for (AuctionStatus to : AuctionStatus.values()) {
            assertThat(AuctionStatus.ENDED.canTransitionTo(to)).isFalse();
            assertThat(AuctionStatus.CANCELLED.canTransitionTo(to)).isFalse();
        }
    }
}
