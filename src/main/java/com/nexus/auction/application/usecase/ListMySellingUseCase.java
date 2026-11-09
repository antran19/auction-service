package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionBidStats;
import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.BidRepositoryPort;
import com.nexus.auction.application.port.out.ProductSnapshotRepositoryPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import com.nexus.common.core.FieldError;
import com.nexus.common.core.exception.ValidationException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// "How are my auctions doing?": the auctions this seller created, newest first, each with how many bids
// and bidders it has drawn. The seller always comes from the token.
public class ListMySellingUseCase {

    private static final int MAX_SIZE = 100;

    private final AuctionRepositoryPort auctionRepositoryPort;
    private final BidRepositoryPort bidRepositoryPort;
    private final ProductSnapshotRepositoryPort productSnapshotRepositoryPort;

    public ListMySellingUseCase(AuctionRepositoryPort auctionRepositoryPort, BidRepositoryPort bidRepositoryPort,
                                ProductSnapshotRepositoryPort productSnapshotRepositoryPort) {
        this.auctionRepositoryPort = auctionRepositoryPort;
        this.bidRepositoryPort = bidRepositoryPort;
        this.productSnapshotRepositoryPort = productSnapshotRepositoryPort;
    }

    // status is optional (null or blank means every status).
    public List<MySellingItem> list(String sellerId, String status, int page, int size) {
        String statusFilter = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        List<FieldError> errors = new ArrayList<>();
        if (statusFilter != null && Arrays.stream(AuctionStatus.values()).noneMatch(s -> s.name().equals(statusFilter))) {
            errors.add(new FieldError("status", "Status must be one of " + Arrays.toString(AuctionStatus.values())));
        }
        if (page < 0) {
            errors.add(new FieldError("page", "Page must be greater than or equal to 0"));
        }
        if (size < 1 || size > MAX_SIZE) {
            errors.add(new FieldError("size", "Size must be between 1 and " + MAX_SIZE));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        List<Auction> auctions = auctionRepositoryPort.search(statusFilter, sellerId, null, page, size);
        if (auctions.isEmpty()) {
            return List.of();
        }
        Map<String, AuctionBidStats> stats = bidRepositoryPort
                .statsByAuctionIds(auctions.stream().map(Auction::getId).toList()).stream()
                .collect(Collectors.toMap(AuctionBidStats::auctionId, Function.identity()));
        // A product whose name is not known yet simply has none: the caller shows its own fallback.
        Map<String, String> productNames = productSnapshotRepositoryPort.findNamesByIds(
                auctions.stream().map(Auction::getProductId).distinct().toList());

        return auctions.stream().map(a -> {
            AuctionBidStats s = stats.get(a.getId());
            return new MySellingItem(a.getId(), a.getProductId(), productNames.get(a.getProductId()),
                    a.getStatus().name(), a.getStartingPrice(), a.getBidIncrement(), a.getCurrentHighestBid(),
                    a.getStartTime(), a.getEndTime(), s == null ? 0 : s.bidCount(), s == null ? 0 : s.bidderCount(),
                    a.getWinnerId(), a.getFinalPrice(), a.getPaymentDeadline(), a.getPaidAt());
        }).toList();
    }
}
