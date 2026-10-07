-- NULL = ended but not yet paid; a timestamp = paid. No new AuctionStatus value needed --
-- status stays ENDED either way, this column is what distinguishes "awaiting payment" from
-- "settled", mirroring the existing payment_deadline/payment_timeout_emitted columns (V1).
ALTER TABLE auctions ADD COLUMN paid_at TIMESTAMPTZ;
