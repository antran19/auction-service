package com.nexus.auction.infrastructure.messaging;

import com.nexus.auction.infrastructure.persistence.OutboxJpaRepository;
import com.nexus.auction.infrastructure.persistence.entity.OutboxJpaEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxRelayJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayJob.class);
    private static final String TOPIC = "auction-events";

    private final OutboxJpaRepository outboxJpaRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelayJob(OutboxJpaRepository outboxJpaRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxJpaRepository = outboxJpaRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    public synchronized void relayPendingEvents() {
        List<OutboxJpaEntity> pending = outboxJpaRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxJpaEntity row : pending) {
            try {
                kafkaTemplate.send(TOPIC, row.getAggregateId(), row.getPayload()).get(5, TimeUnit.SECONDS);
                row.markPublished(Instant.now());
                outboxJpaRepository.save(row);
            } catch (Exception e) {
                log.error("Failed to publish outbox row {} (event {}) — will retry on next poll",
                        row.getId(), row.getEventType(), e);
            }
        }
    }
}
