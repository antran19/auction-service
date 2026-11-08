package com.nexus.auction.domain.model;

// What auction-service knows about a catalog product: who owns it and whether it is on sale.
// Status is the catalog's own value (DRAFT/ACTIVE/INACTIVE/SOLD), kept as a string.
public class AuctionProduct {

    private static final String AUCTIONABLE_STATUS = "ACTIVE";

    private final String productId;
    private final String sellerId;
    private final String status;
    // null until the catalog tells us (rows older than the name column)
    private final String name;

    private AuctionProduct(String productId, String sellerId, String status, String name) {
        this.productId = productId;
        this.sellerId = sellerId;
        this.status = status;
        this.name = name;
    }

    // Catalog creates every product as DRAFT.
    public static AuctionProduct create(String productId, String sellerId) {
        return create(productId, sellerId, null);
    }

    public static AuctionProduct create(String productId, String sellerId, String name) {
        return new AuctionProduct(productId, sellerId, "DRAFT", name);
    }

    public static AuctionProduct reconstitute(String productId, String sellerId, String status) {
        return reconstitute(productId, sellerId, status, null);
    }

    public static AuctionProduct reconstitute(String productId, String sellerId, String status, String name) {
        return new AuctionProduct(productId, sellerId, status, name);
    }

    public AuctionProduct withStatus(String newStatus) {
        return new AuctionProduct(productId, sellerId, newStatus, name);
    }

    public AuctionProduct withName(String newName) {
        return new AuctionProduct(productId, sellerId, status, newName);
    }

    public boolean isAuctionable() {
        return AUCTIONABLE_STATUS.equals(status);
    }

    public boolean isOwnedBy(String candidateSellerId) {
        return sellerId.equals(candidateSellerId);
    }

    public String getProductId() { return productId; }
    public String getSellerId() { return sellerId; }
    public String getStatus() { return status; }
    public String getName() { return name; }
}
