package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.common.core.FieldError;
import com.nexus.common.core.exception.ConflictException;
import com.nexus.common.core.exception.ValidationException;
import com.nexus.common.events.AuctionCreatedEvent;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

public class CreateAuctionUseCase {

    private static final long MIN_DURATION_MINUTES = 60;
    private static final long MAX_DURATION_HOURS = 168;
    private static final int MAX_ACTIVE_AUCTIONS_PER_SELLER = 5;

    private final AuctionRepositoryPort auctionRepositoryPort;
    private final EventPublisherPort eventPublisherPort;

    public CreateAuctionUseCase(AuctionRepositoryPort auctionRepositoryPort, EventPublisherPort eventPublisherPort) {
        this.auctionRepositoryPort = auctionRepositoryPort;
        this.eventPublisherPort = eventPublisherPort;
    }

    @Transactional
    public AuctionResult create(CreateAuctionCommand command) {
        validateWindow(command);

        if (auctionRepositoryPort.existsActiveOrPendingForProduct(command.productId())) {
            throw new ConflictException("PRODUCT_ALREADY_IN_AUCTION",
                    "Product already has an active or pending auction: " + command.productId());
        }
        if (auctionRepositoryPort.countActiveOrPendingBySeller(command.sellerId()) >= MAX_ACTIVE_AUCTIONS_PER_SELLER) {
            throw new ConflictException("TOO_MANY_ACTIVE_AUCTIONS",
                    "Seller already has " + MAX_ACTIVE_AUCTIONS_PER_SELLER + " active or pending auctions");
        }

        Auction auction = Auction.create(command.productId(), command.sellerId(), command.startingPrice(),
                command.bidIncrement(), command.startTime(), command.endTime());
        Auction saved = auctionRepositoryPort.save(auction);

        eventPublisherPort.publish(new AuctionCreatedEvent(saved.getId(), saved.getProductId(), saved.getSellerId()));

        return toResult(saved);
    }

    private void validateWindow(CreateAuctionCommand command) {
        if (!command.startTime().isBefore(command.endTime())) {
            throw new ValidationException(List.of(
                    new FieldError("endTime", "endTime must be after startTime")));
        }
        Duration duration = Duration.between(command.startTime(), command.endTime());
        if (duration.toMinutes() < MIN_DURATION_MINUTES) {
            throw new ValidationException(List.of(new FieldError("endTime",
                    "Auction duration must be at least " + MIN_DURATION_MINUTES + " minutes")));
        }
        if (duration.toHours() > MAX_DURATION_HOURS) {
            throw new ValidationException(List.of(new FieldError("endTime",
                    "Auction duration must not exceed " + MAX_DURATION_HOURS + " hours")));
        }
    }

    static AuctionResult toResult(Auction a) {
        return new AuctionResult(a.getId(), a.getProductId(), a.getSellerId(), a.getStartingPrice(),
                a.getBidIncrement(), a.getCurrentHighestBid(), a.getCurrentHighestBidderId(), a.getStatus().name(),
                a.getStartTime(), a.getEndTime(), a.getExtensionCount(), a.getWinnerId(), a.getFinalPrice(),
                a.getPaymentDeadline());
    }
}
