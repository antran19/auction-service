package com.nexus.auction.infrastructure.streaming;

import com.nexus.auction.application.port.out.BidBroadcastPort;
import com.nexus.auction.application.usecase.BidResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// Keeps the open SSE connections of each auction in memory and pushes every accepted bid to them.
// In memory means: one auction-service instance only. With several instances a watcher would see
// only the bids that landed on its own instance (a broker such as Redis would be needed).
@Component
public class SseBidBroadcaster implements BidBroadcastPort {

    private static final Logger log = LoggerFactory.getLogger(SseBidBroadcaster.class);
    static final long TIMEOUT_MILLIS = 30L * 60 * 1000;

    private final Map<String, List<SseEmitter>> emittersByAuction = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String auctionId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        List<SseEmitter> emitters = emittersByAuction.computeIfAbsent(auctionId, id -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);
        Runnable remove = () -> remove(auctionId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        try {
            // Sends the response headers now, so the client knows the stream is open.
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException | IllegalStateException e) {
            remove.run();
        }
        return emitter;
    }

    // A bid that is rolled back must never reach a watcher, so inside a transaction the push
    // waits for the commit.
    @Override
    public void bidPlaced(BidResult bid) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push(bid);
                }
            });
        } else {
            push(bid);
        }
    }

    int subscriberCount(String auctionId) {
        List<SseEmitter> emitters = emittersByAuction.get(auctionId);
        return emitters == null ? 0 : emitters.size();
    }

    private void push(BidResult bid) {
        List<SseEmitter> emitters = emittersByAuction.get(bid.auctionId());
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("bid").data(bid, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                // The watcher went away: drop it, the bid itself is already stored.
                log.debug("Dropping a closed stream of auction {}", bid.auctionId());
                remove(bid.auctionId(), emitter);
            }
        }
    }

    private void remove(String auctionId, SseEmitter emitter) {
        emittersByAuction.computeIfPresent(auctionId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
