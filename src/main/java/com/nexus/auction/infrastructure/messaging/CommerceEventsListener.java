package com.nexus.auction.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.auction.application.usecase.EmitPaymentTimeoutUseCase;
import com.nexus.auction.application.usecase.MarkAuctionPaidUseCase;
import com.nexus.common.events.OrderCancelledEvent;
import com.nexus.common.events.OrderPaidEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// Commerce Service now owns payment entirely (see CommerceServiceApplication's
// ConfirmPaymentUseCase). This listener is how auction-service learns a winner has paid
// (OrderPaid) or backed out before paying (OrderCancelled), replacing the old HTTP-driven
// ConfirmPaymentUseCase that used to call Stripe directly. Only events carrying an
// auctionId are relevant -- a direct-purchase order (no auction involved) has auctionId
// null and is ignored here.
@Component
public class CommerceEventsListener {

    private static final Logger log = LoggerFactory.getLogger(CommerceEventsListener.class);

    private final ObjectMapper objectMapper;
    private final MarkAuctionPaidUseCase markAuctionPaidUseCase;
    private final EmitPaymentTimeoutUseCase emitPaymentTimeoutUseCase;

    public CommerceEventsListener(ObjectMapper objectMapper, MarkAuctionPaidUseCase markAuctionPaidUseCase,
                                   EmitPaymentTimeoutUseCase emitPaymentTimeoutUseCase) {
        this.objectMapper = objectMapper;
        this.markAuctionPaidUseCase = markAuctionPaidUseCase;
        this.emitPaymentTimeoutUseCase = emitPaymentTimeoutUseCase;
    }

    @KafkaListener(topics = "commerce-events")
    public void onMessage(String rawPayload) {
        try {
            String eventType = objectMapper.readTree(rawPayload).path("eventType").asText();
            if ("OrderPaid".equals(eventType)) {
                OrderPaidEvent event = objectMapper.readValue(rawPayload, OrderPaidEvent.class);
                if (event.getAuctionId() != null) {
                    markAuctionPaidUseCase.markPaid(event.getAuctionId(), event.getBuyerId());
                }
            } else if ("OrderCancelled".equals(eventType)) {
                // The buyer backed out before the 24h deadline -- no need to wait for
                // PaymentDeadlineJob to find out; EmitPaymentTimeoutUseCase is already
                // idempotent, so the job finding this auction too (if it ever raced ahead)
                // is a harmless no-op.
                OrderCancelledEvent event = objectMapper.readValue(rawPayload, OrderCancelledEvent.class);
                if (event.getAuctionId() != null) {
                    emitPaymentTimeoutUseCase.emit(event.getAuctionId());
                }
            }
        } catch (Exception e) {
            log.error("Failed to process commerce-events message: {}", rawPayload, e);
        }
    }
}
