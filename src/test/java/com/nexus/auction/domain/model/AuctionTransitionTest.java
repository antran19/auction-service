package com.nexus.auction.domain.model;

import com.nexus.common.core.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuctionTransitionTest {

    private Auction pending() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        return Auction.create(UUID.randomUUID().toString(), "seller-1", new BigDecimal("100.00"),
                new BigDecimal("10.00"), start, start.plus(2, ChronoUnit.HOURS));
    }

    @Test
    void withStatus_followsThePendingActiveEndedLife_andLeavesTheOriginalUntouched() {
        Auction pending = pending();

        Auction active = pending.withStatus(AuctionStatus.ACTIVE);
        Auction ended = active.withStatus(AuctionStatus.ENDED);

        assertThat(pending.getStatus()).isEqualTo(AuctionStatus.PENDING);
        assertThat(active.getStatus()).isEqualTo(AuctionStatus.ACTIVE);
        assertThat(ended.getStatus()).isEqualTo(AuctionStatus.ENDED);
        assertThat(ended.getId()).isEqualTo(pending.getId());
    }

    @Test
    void withStatus_canCancelFromEitherLiveStatus() {
        assertThat(pending().withStatus(AuctionStatus.CANCELLED).getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        assertThat(pending().withStatus(AuctionStatus.ACTIVE).withStatus(AuctionStatus.CANCELLED).getStatus())
                .isEqualTo(AuctionStatus.CANCELLED);
    }

    @Test
    void withStatus_refusesAMoveTheLifecycleDoesNotAllow() {
        Auction pending = pending();
        Auction ended = pending.withStatus(AuctionStatus.ACTIVE).withStatus(AuctionStatus.ENDED);
        Auction cancelled = pending.withStatus(AuctionStatus.CANCELLED);

        assertThatThrownBy(() -> pending.withStatus(AuctionStatus.ENDED))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("INVALID_AUCTION_TRANSITION"));
        assertThatThrownBy(() -> ended.withStatus(AuctionStatus.ACTIVE)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> ended.withStatus(AuctionStatus.PENDING)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> cancelled.withStatus(AuctionStatus.ACTIVE)).isInstanceOf(ConflictException.class);
    }
}
