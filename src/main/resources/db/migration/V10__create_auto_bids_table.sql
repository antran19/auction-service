-- A bidder's standing instruction: "keep bidding for me on this auction, one increment at a time,
-- up to max_amount". At most one per bidder per auction (raising the maximum updates the row).
-- created_at never changes after the first insert: when two bidders have the same maximum, the
-- one who set it first wins, so the order must survive later updates.
-- auction_id has a real foreign key because both tables live in this service's own database.
CREATE TABLE auto_bids (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auction_id UUID NOT NULL REFERENCES auctions(id),
    bidder_id VARCHAR(255) NOT NULL,
    max_amount NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_auto_bids_auction_bidder UNIQUE (auction_id, bidder_id),
    CONSTRAINT chk_auto_bids_max_positive CHECK (max_amount > 0)
);

CREATE INDEX idx_auto_bids_auction_id ON auto_bids (auction_id);
