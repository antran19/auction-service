CREATE TABLE auctions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL,
    seller_id VARCHAR(255) NOT NULL,
    starting_price NUMERIC(12,2) NOT NULL,
    bid_increment NUMERIC(12,2) NOT NULL,
    current_highest_bid NUMERIC(12,2),
    current_highest_bidder_id VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    extension_count INT NOT NULL DEFAULT 0,
    winner_id VARCHAR(255),
    final_price NUMERIC(12,2),
    payment_deadline TIMESTAMPTZ,
    payment_timeout_emitted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_auctions_product_id ON auctions (product_id);
CREATE INDEX idx_auctions_seller_id ON auctions (seller_id);
CREATE INDEX idx_auctions_status ON auctions (status);
