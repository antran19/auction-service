package com.nexus.auction.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.auction.application.usecase.MarkAuctionPaidUseCase;
import com.nexus.common.events.OrderPaidEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// Commerce Service now owns payment entirely (see CommerceServiceApplication's
// ConfirmPaymentUseCase). This listener is how auction-service learns a winner has paid,
// replacing the old HTTP-driven ConfirmPaymentUseCase that used to call Stripe directly.
// Only OrderPaid events carrying an auctionId are relevant -- a direct-purchase order (no
// auction involved) has auctionId null and is ignored here.
@Component
public class CommerceEventsListener {

    private static final Logger log = LoggerFactory.getLogger(CommerceEventsListener.class);

    private final ObjectMapper objectMapper;
    private final MarkAuctionPaidUseCase markAuctionPaidUseCase;

    public CommerceEventsListener(ObjectMapper objectMapper, MarkAuctionPaidUseCase markAuctionPaidUseCase) {
        this.objectMapper = objectMapper;
        this.markAuctionPaidUseCase = markAuctionPaidUseCase;
    }

    @KafkaListener(topics = "commerce-events")
    public void onMessage(String rawPayload) {
        try {
            String eventType = objectMapper.readTree(rawPayload).path("eventType").asText();
            if (!"OrderPaid".equals(eventType)) {
                return;
            }
            OrderPaidEvent event = objectMapper.readValue(rawPayload, OrderPaidEvent.class);
            if (event.getAuctionId() == null) {
                return;
            }
            markAuctionPaidUseCase.markPaid(event.getAuctionId(), event.getBuyerId());
        } catch (Exception e) {
            log.error("Failed to process commerce-events message: {}", rawPayload, e);
        }
    }
}
