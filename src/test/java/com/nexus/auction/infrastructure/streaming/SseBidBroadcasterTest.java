package com.nexus.auction.infrastructure.streaming;

import com.nexus.auction.application.usecase.BidResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SseBidBroadcasterTest {

    private final SseBidBroadcaster broadcaster = new SseBidBroadcaster();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static BidResult bid(String auctionId) {
        return new BidResult("bid-1", auctionId, "bidder-1", new BigDecimal("120.00"), Instant.now());
    }

    @Test
    void subscribe_registersTheWatcherOfThatAuctionOnly() {
        broadcaster.subscribe("auction-1");
        broadcaster.subscribe("auction-1");
        broadcaster.subscribe("auction-2");

        assertThat(broadcaster.subscriberCount("auction-1")).isEqualTo(2);
        assertThat(broadcaster.subscriberCount("auction-2")).isEqualTo(1);
        assertThat(broadcaster.subscriberCount("auction-3")).isZero();
    }

    @Test
    void aWatcherWhoseStreamIsAlreadyClosed_isDroppedWhenABidIsPushed() {
        SseEmitter emitter = broadcaster.subscribe("auction-1");
        emitter.complete();

        broadcaster.bidPlaced(bid("auction-1"));

        assertThat(broadcaster.subscriberCount("auction-1")).isZero();
    }

    @Test
    void aBidForAnAuctionNobodyWatches_isIgnored() {
        assertThatCode(() -> broadcaster.bidPlaced(bid("auction-9"))).doesNotThrowAnyException();
    }

    @Test
    void insideATransaction_theBidIsOnlyPushedAfterCommit() {
        SseEmitter emitter = broadcaster.subscribe("auction-1");
        TransactionSynchronizationManager.initSynchronization();

        broadcaster.bidPlaced(bid("auction-1"));

        // Nothing sent yet and the watcher is still registered: the push is waiting for the commit.
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        assertThat(broadcaster.subscriberCount("auction-1")).isEqualTo(1);

        // A rollback never calls afterCommit: simply drop the synchronizations.
        TransactionSynchronizationManager.clearSynchronization();
        assertThat(emitter).isNotNull();
        assertThat(broadcaster.subscriberCount("auction-1")).isEqualTo(1);
    }

    @Test
    void afterCommit_theBidIsPushed() {
        SseEmitter emitter = broadcaster.subscribe("auction-1");
        emitter.complete();
        TransactionSynchronizationManager.initSynchronization();
        broadcaster.bidPlaced(bid("auction-1"));

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // The push reached the emitter: it was closed, so the send failed and the watcher was dropped.
        assertThat(broadcaster.subscriberCount("auction-1")).isZero();
    }
}
