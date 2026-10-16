package com.nexus.auction.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.auction.application.usecase.ClosePaymentWithoutPenaltyUseCase;
import com.nexus.auction.application.usecase.EmitPaymentTimeoutUseCase;
import com.nexus.auction.application.usecase.MarkAuctionPaidUseCase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CommerceEventsListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final MarkAuctionPaidUseCase markAuctionPaidUseCase = mock(MarkAuctionPaidUseCase.class);
    private final EmitPaymentTimeoutUseCase emitPaymentTimeoutUseCase = mock(EmitPaymentTimeoutUseCase.class);
    private final ClosePaymentWithoutPenaltyUseCase closePaymentWithoutPenaltyUseCase =
            mock(ClosePaymentWithoutPenaltyUseCase.class);
    private final CommerceEventsListener listener = new CommerceEventsListener(objectMapper,
            markAuctionPaidUseCase, emitPaymentTimeoutUseCase, closePaymentWithoutPenaltyUseCase);

    @Test
    void onMessage_orderPaidWithAuctionId_marksTheAuctionPaid() throws Exception {
        String payload = "{\"eventId\":\"e-1\",\"eventType\":\"OrderPaid\",\"occurredAt\":\"2026-01-01T00:00:00Z\","
                + "\"aggregateId\":\"order-1\",\"orderId\":\"order-1\",\"buyerId\":\"buyer-1\","
                + "\"auctionId\":\"auction-1\",\"amount\":500.00}";

        listener.onMessage(payload);

        verify(markAuctionPaidUseCase).markPaid("auction-1", "buyer-1");
        verify(emitPaymentTimeoutUseCase, never()).emit(any());
    }

    @Test
    void onMessage_orderPaidWithoutAuctionId_doesNothing() throws Exception {
        String payload = "{\"eventId\":\"e-2\",\"eventType\":\"OrderPaid\",\"occurredAt\":\"2026-01-01T00:00:00Z\","
                + "\"aggregateId\":\"order-2\",\"orderId\":\"order-2\",\"buyerId\":\"buyer-1\","
                + "\"auctionId\":null,\"amount\":500.00}";

        listener.onMessage(payload);

        verify(markAuctionPaidUseCase, never()).markPaid(any(), any());
    }

    @Test
    void onMessage_orderCancelledWithAuctionId_emitsPaymentTimeoutImmediately() throws Exception {
        String payload = "{\"eventId\":\"e-3\",\"eventType\":\"OrderCancelled\",\"occurredAt\":\"2026-01-01T00:00:00Z\","
                + "\"aggregateId\":\"order-3\",\"orderId\":\"order-3\",\"reason\":\"BUYER_REQUESTED\","
                + "\"auctionId\":\"auction-3\"}";

        listener.onMessage(payload);

        verify(emitPaymentTimeoutUseCase).emit("auction-3");
        verify(markAuctionPaidUseCase, never()).markPaid(any(), any());
        verify(closePaymentWithoutPenaltyUseCase, never()).close(any());
    }

    @Test
    void onMessage_orderCancelledForStock_closesPaymentWithoutPenalty() throws Exception {
        String payload = "{\"eventId\":\"e-6\",\"eventType\":\"OrderCancelled\",\"occurredAt\":\"2026-01-01T00:00:00Z\","
                + "\"aggregateId\":\"order-6\",\"orderId\":\"order-6\",\"reason\":\"INSUFFICIENT_STOCK\","
                + "\"auctionId\":\"auction-6\"}";

        listener.onMessage(payload);

        verify(closePaymentWithoutPenaltyUseCase).close("auction-6");
        verify(emitPaymentTimeoutUseCase, never()).emit(any());
    }

    @Test
    void onMessage_orderCancelledWithoutAuctionId_doesNothing() throws Exception {
        String payload = "{\"eventId\":\"e-4\",\"eventType\":\"OrderCancelled\",\"occurredAt\":\"2026-01-01T00:00:00Z\","
                + "\"aggregateId\":\"order-4\",\"orderId\":\"order-4\",\"reason\":\"BUYER_REQUESTED\","
                + "\"auctionId\":null}";

        listener.onMessage(payload);

        verify(emitPaymentTimeoutUseCase, never()).emit(any());
    }

    @Test
    void onMessage_otherEventType_doesNothing() throws Exception {
        String payload = "{\"eventId\":\"e-5\",\"eventType\":\"SomethingElse\",\"occurredAt\":\"2026-01-01T00:00:00Z\","
                + "\"aggregateId\":\"x\"}";

        listener.onMessage(payload);

        verify(markAuctionPaidUseCase, never()).markPaid(any(), any());
        verify(emitPaymentTimeoutUseCase, never()).emit(any());
    }

    @Test
    void onMessage_malformedPayload_throwsSoItGoesToDeadLetterTopic() throws Exception {
        assertThatThrownBy(() -> listener.onMessage("{not valid json"))
                .isInstanceOf(JsonProcessingException.class);
    }
}
