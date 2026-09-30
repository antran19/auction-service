package com.nexus.auction.application.usecase;

import com.nexus.auction.application.exception.AuctionNotFoundException;
import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import com.nexus.common.core.exception.ConflictException;
import com.nexus.common.core.exception.ForbiddenException;
import com.nexus.common.core.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UpdateAuctionUseCaseTest {

    private AuctionRepositoryPort auctionRepositoryPort;
    private UpdateAuctionUseCase useCase;

    @BeforeEach
    void setUp() {
        auctionRepositoryPort = mock(AuctionRepositoryPort.class);
        useCase = new UpdateAuctionUseCase(auctionRepositoryPort);
        when(auctionRepositoryPort.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));
        // findById is stubbed to a wrong value on purpose: update() must use the locked
        // read (findByIdForUpdate), never the unlocked one, or a concurrent bid/settlement
        // between the unlocked read and the save() could be silently overwritten. If update()
        // regresses to findById, this stub makes every test fail with AuctionNotFoundException
        // instead of passing by accident.
        when(auctionRepositoryPort.findById(any())).thenThrow(new AssertionError(
                "UpdateAuctionUseCase must call findByIdForUpdate, not findById"));
    }

    private Auction pendingAuction() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        return Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                start, start.plus(2, ChronoUnit.HOURS));
    }

    @Test
    void update_updatesPriceIncrementAndWindowWhilePending() {
        Auction auction = pendingAuction();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));
        Instant newStart = Instant.now().plus(3, ChronoUnit.HOURS);

        AuctionResult result = useCase.update(auction.getId(), new BigDecimal("150.00"), new BigDecimal("15.00"),
                newStart, newStart.plus(2, ChronoUnit.HOURS), "seller-1");

        assertThat(result.startingPrice()).isEqualByComparingTo("150.00");
        assertThat(result.bidIncrement()).isEqualByComparingTo("15.00");
    }

    @Test
    void update_rejectsNonOwner() {
        Auction auction = pendingAuction();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> useCase.update(auction.getId(), new BigDecimal("150.00"),
                new BigDecimal("15.00"), auction.getStartTime(), auction.getEndTime(), "other-seller"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void update_rejectsWhenAuctionNotPending() {
        Auction active = pendingAuction().withStatus(AuctionStatus.ACTIVE);
        when(auctionRepositoryPort.findByIdForUpdate(active.getId())).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> useCase.update(active.getId(), new BigDecimal("150.00"),
                new BigDecimal("15.00"), active.getStartTime(), active.getEndTime(), "seller-1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_throwsNotFoundForUnknownId() {
        when(auctionRepositoryPort.findByIdForUpdate("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.update("missing", new BigDecimal("150.00"),
                new BigDecimal("15.00"), Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS), "seller-1"))
                .isInstanceOf(AuctionNotFoundException.class);
    }

    // --- Fix-pass additions (2026-09-30 final review): update() must re-validate the
    // same duration window CreateAuctionUseCase enforces, or a seller can update a valid
    // auction into an invalid one (end before start, too short, too long). ---

    @Test
    void update_rejectsEndTimeBeforeStartTime() {
        Auction auction = pendingAuction();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);

        assertThatThrownBy(() -> useCase.update(auction.getId(), new BigDecimal("150.00"), new BigDecimal("15.00"),
                start, start.minus(1, ChronoUnit.HOURS), "seller-1"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void update_rejectsDurationBelowMinimum() {
        Auction auction = pendingAuction();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);

        assertThatThrownBy(() -> useCase.update(auction.getId(), new BigDecimal("150.00"), new BigDecimal("15.00"),
                start, start.plus(30, ChronoUnit.MINUTES), "seller-1"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void update_rejectsDurationAboveMaximum() {
        Auction auction = pendingAuction();
        when(auctionRepositoryPort.findByIdForUpdate(auction.getId())).thenReturn(Optional.of(auction));
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);

        assertThatThrownBy(() -> useCase.update(auction.getId(), new BigDecimal("150.00"), new BigDecimal("15.00"),
                start, start.plus(169, ChronoUnit.HOURS), "seller-1"))
                .isInstanceOf(ValidationException.class);
    }
}
