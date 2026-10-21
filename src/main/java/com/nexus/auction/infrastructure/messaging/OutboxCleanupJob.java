package com.nexus.auction.infrastructure.messaging;

import com.nexus.auction.infrastructure.persistence.OutboxJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

// Every event leaves one row in the outbox table; once OutboxRelayJob has published it the row has
// no further use except for investigating an incident. This deletes rows that were published more
// than "retention-days" ago. Rows that are NOT published yet are never touched, however old:
// they are events still waiting to go out.
@Component
public class OutboxCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupJob.class);

    private final OutboxJpaRepository outboxJpaRepository;
    private final Duration retention;

    public OutboxCleanupJob(OutboxJpaRepository outboxJpaRepository,
                            @Value("${nexus.outbox.retention-days:7}") long retentionDays) {
        this.outboxJpaRepository = outboxJpaRepository;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Scheduled(initialDelayString = "${nexus.outbox.cleanup-initial-delay-ms:60000}",
               fixedDelayString = "${nexus.outbox.cleanup-interval-ms:3600000}")
    public void cleanup() {
        cleanupAt(Instant.now());
    }

    int cleanupAt(Instant now) {
        int removed = outboxJpaRepository.deletePublishedBefore(now.minus(retention));
        if (removed > 0) {
            log.info("Removed {} outbox row(s) published more than {} day(s) ago", removed, retention.toDays());
        }
        return removed;
    }
}
