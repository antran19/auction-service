package com.nexus.auction.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

final class UuidIds {

    private UuidIds() {
    }

    static Optional<UUID> tryParse(String id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
