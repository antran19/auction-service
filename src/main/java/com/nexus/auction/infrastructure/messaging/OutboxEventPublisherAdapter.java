package com.nexus.auction.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.infrastructure.persistence.OutboxJpaRepository;
import com.nexus.auction.infrastructure.persistence.entity.OutboxJpaEntity;
import com.nexus.common.events.DomainEvent;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OutboxEventPublisherAdapter implements EventPublisherPort {

    private final OutboxJpaRepository outboxJpaRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisherAdapter(OutboxJpaRepository outboxJpaRepository, ObjectMapper objectMapper) {
        this.outboxJpaRepository = outboxJpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(DomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxJpaEntity entity = new OutboxJpaEntity(
                    UUID.randomUUID(), event.getAggregateId(), event.getEventType(), payload, event.getOccurredAt());
            outboxJpaRepository.save(entity);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize event for outbox: " + event.getEventType(), e);
        }
    }
}
