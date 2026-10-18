package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.application.port.out.ProductSnapshotRepositoryPort;
import com.nexus.auction.application.port.out.ProductStockRepositoryPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionProduct;
import com.nexus.common.core.FieldError;
import com.nexus.common.core.exception.ConflictException;
import com.nexus.common.core.exception.ForbiddenException;
import com.nexus.common.core.exception.ValidationException;
import com.nexus.common.events.AuctionCreatedEvent;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class CreateAuctionUseCase {

    static final long MIN_DURATION_MINUTES = 60;
    static final long MAX_DURATION_HOURS = 168;
    private static final int MAX_ACTIVE_AUCTIONS_PER_SELLER = 5;

    private final AuctionRepositoryPort auctionRepositoryPort;
    private final EventPublisherPort eventPublisherPort;
    private final ProductSnapshotRepositoryPort productSnapshotRepositoryPort;
    private final ProductStockRepositoryPort productStockRepositoryPort;

    public CreateAuctionUseCase(AuctionRepositoryPort auctionRepositoryPort, EventPublisherPort eventPublisherPort,
                                ProductSnapshotRepositoryPort productSnapshotRepositoryPort,
                                ProductStockRepositoryPort productStockRepositoryPort) {
        this.auctionRepositoryPort = auctionRepositoryPort;
        this.eventPublisherPort = eventPublisherPort;
        this.productSnapshotRepositoryPort = productSnapshotRepositoryPort;
        this.productStockRepositoryPort = productStockRepositoryPort;
    }

    @Transactional
    public AuctionResult create(CreateAuctionCommand command) {
        // SRS: MIN_REPUTATION_TO_SELL. A null/missing trustLevel (old token, or a test not
        // exercising this path) fails open -- see PlaceBidUseCase for the same reasoning.
        if (command.sellerTrustLevel() != null && !"TRUSTED".equals(command.sellerTrustLevel())) {
            throw new ForbiddenException("INSUFFICIENT_TRUST_TO_CREATE_AUCTION",
                    "Your reputation score is too low to create auctions");
        }

        validateWindow(command.startTime(), command.endTime());
        requireOwnProductInStock(command.productId(), command.sellerId());

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

    // The winner's order is filed under the auction's seller but takes the PRODUCT's stock, so a
    // seller auctioning someone else's product would take the money for goods that aren't theirs.
    // Checked against auction-service's own copy of the catalog and of the stock (fed by events).
    // The unit is not held for the auction's duration: if it sells elsewhere meanwhile, the
    // winner's order is cancelled for lack of stock without penalising the winner.
    private void requireOwnProductInStock(String productId, String sellerId) {
        AuctionProduct product = productSnapshotRepositoryPort.findById(productId)
                .filter(AuctionProduct::isAuctionable)
                .orElseThrow(() -> new ConflictException("PRODUCT_NOT_AVAILABLE",
                        "Product is not available for auction: " + productId));
        if (!product.isOwnedBy(sellerId)) {
            throw new ForbiddenException("NOT_THE_PRODUCT_OWNER", "You can only auction your own products");
        }
        if (productStockRepositoryPort.availableQuantity(productId) < 1) {
            throw new ConflictException("INSUFFICIENT_STOCK", "Product has no stock left to auction: " + productId);
        }
    }

    // Package-private (not private) so UpdateAuctionUseCase — same package — reuses the
    // exact same duration bounds. A seller updating an auction's time window must be held
    // to the same rules as creating one, or update() becomes a way to bypass them.
    static void validateWindow(Instant startTime, Instant endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new ValidationException(List.of(
                    new FieldError("endTime", "endTime must be after startTime")));
        }
        Duration duration = Duration.between(startTime, endTime);
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
                a.getPaymentDeadline(), a.getPaidAt());
    }
}
