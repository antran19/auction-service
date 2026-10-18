package com.nexus.auction.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.auction.application.usecase.SyncProductSnapshotUseCase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CatalogEventsListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SyncProductSnapshotUseCase syncProductSnapshotUseCase = mock(SyncProductSnapshotUseCase.class);
    private final CatalogEventsListener listener = new CatalogEventsListener(objectMapper, syncProductSnapshotUseCase);

    @Test
    void onMessage_productCreated_recordsTheOwner() throws Exception {
        listener.onMessage("{\"eventId\":\"e-1\",\"eventType\":\"ProductCreated\",\"occurredAt\":\"2026-01-01T00:00:00Z\","
                + "\"aggregateId\":\"product-1\",\"productId\":\"product-1\",\"name\":\"Watch\",\"categoryId\":\"c\","
                + "\"sellerId\":\"seller-1\",\"initialStock\":1,\"price\":10}");

        verify(syncProductSnapshotUseCase).onCreated("product-1", "seller-1");
    }

    @Test
    void onMessage_productStatusChanged_updatesTheStatus() throws Exception {
        listener.onMessage("{\"eventId\":\"e-2\",\"eventType\":\"ProductStatusChanged\",\"occurredAt\":\"2026-01-01T00:00:00Z\","
                + "\"aggregateId\":\"product-1\",\"productId\":\"product-1\",\"oldStatus\":\"DRAFT\",\"newStatus\":\"ACTIVE\"}");

        verify(syncProductSnapshotUseCase).onStatusChanged("product-1", "ACTIVE");
    }

    @Test
    void onMessage_otherEvents_areIgnored() throws Exception {
        listener.onMessage("{\"eventId\":\"e-3\",\"eventType\":\"ProductUpdated\",\"occurredAt\":\"2026-01-01T00:00:00Z\","
                + "\"aggregateId\":\"product-1\",\"productId\":\"product-1\",\"name\":\"New\"}");

        verifyNoInteractions(syncProductSnapshotUseCase);
    }

    @Test
    void onMessage_malformedPayload_throwsSoItGoesToDeadLetterTopic() {
        assertThatThrownBy(() -> listener.onMessage("{not json")).isInstanceOf(JsonProcessingException.class);
    }
}
