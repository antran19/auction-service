package com.nexus.auction.domain.model;

public enum AuctionStatus {
    PENDING, ACTIVE, ENDED, CANCELLED;

    // PENDING -> ACTIVE -> ENDED is the normal life. CANCELLED can be reached from either live
    // state; ENDED and CANCELLED are final. Everything else is refused, so a job that races a
    // manual action can never, for example, reopen an auction that has already ended.
    public boolean canTransitionTo(AuctionStatus next) {
        return switch (this) {
            case PENDING -> next == ACTIVE || next == CANCELLED;
            case ACTIVE -> next == ENDED || next == CANCELLED;
            case ENDED, CANCELLED -> false;
        };
    }
}
