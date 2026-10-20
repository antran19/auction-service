package com.nexus.auction.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.auction.application.usecase.ClosePaymentWithoutPenaltyUseCase;
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

    // The only cancel reason that is the winner's own choice (commerce's CancelOrderUseCase).
    // Anything else (e.g. fulfillment couldn't reserve stock) is not their fault.
    static final String BUYER_REQUESTED = "BUYER_REQUESTED";

    private final ObjectMapper objectMapper;
    private final MarkAuctionPaidUseCase markAuctionPaidUseCase;
    private final EmitPaymentTimeoutUseCase emitPaymentTimeoutUseCase;
    private final ClosePaymentWithoutPenaltyUseCase closePaymentWithoutPenaltyUseCase;

    public CommerceEventsListener(ObjectMapper objectMapper, MarkAuctionPaidUseCase markAuctionPaidUseCase,
                                   EmitPaymentTimeoutUseCase emitPaymentTimeoutUseCase,
                                   ClosePaymentWithoutPenaltyUseCase closePaymentWithoutPenaltyUseCase) {
        this.objectMapper = objectMapper;
        this.markAuctionPaidUseCase = markAuctionPaidUseCase;
        this.emitPaymentTimeoutUseCase = emitPaymentTimeoutUseCase;
        this.closePaymentWithoutPenaltyUseCase = closePaymentWithoutPenaltyUseCase;
    }

    // Exceptions are NOT caught here on purpose: NexusKafkaAutoConfiguration (common-kafka) retries them, then
    // moves the record to the dead-letter topic instead of losing it.
    @KafkaListener(topics = "commerce-events")
    public void onMessage(String rawPayload) throws JsonProcessingException {
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
            // is a harmless no-op. Any other reason (out of stock, ...) is not the
            // winner's fault: close the deadline without a penalty instead.
            OrderCancelledEvent event = objectMapper.readValue(rawPayload, OrderCancelledEvent.class);
            if (event.getAuctionId() != null) {
                if (BUYER_REQUESTED.equals(event.getReason())) {
                    emitPaymentTimeoutUseCase.emit(event.getAuctionId());
                } else {
                    log.info("Auction {} order cancelled ({}), closing payment without penalty",
                            event.getAuctionId(), event.getReason());
                    closePaymentWithoutPenaltyUseCase.close(event.getAuctionId());
                }
            }
        }
    }
}
