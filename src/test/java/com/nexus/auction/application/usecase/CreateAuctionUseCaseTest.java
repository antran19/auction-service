package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.application.port.out.ProductSnapshotRepositoryPort;
import com.nexus.auction.application.port.out.ProductStockRepositoryPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionProduct;
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

class CreateAuctionUseCaseTest {

    private AuctionRepositoryPort auctionRepositoryPort;
    private EventPublisherPort eventPublisherPort;
    private ProductSnapshotRepositoryPort productSnapshotRepositoryPort;
    private ProductStockRepositoryPort productStockRepositoryPort;
    private CreateAuctionUseCase useCase;

    @BeforeEach
    void setUp() {
        auctionRepositoryPort = mock(AuctionRepositoryPort.class);
        eventPublisherPort = mock(EventPublisherPort.class);
        productSnapshotRepositoryPort = mock(ProductSnapshotRepositoryPort.class);
        productStockRepositoryPort = mock(ProductStockRepositoryPort.class);
        useCase = new CreateAuctionUseCase(auctionRepositoryPort, eventPublisherPort, productSnapshotRepositoryPort,
                productStockRepositoryPort);
        when(productSnapshotRepositoryPort.findById("product-1"))
                .thenReturn(Optional.of(AuctionProduct.create("product-1", "seller-1").withStatus("ACTIVE")));
        when(productStockRepositoryPort.availableQuantity("product-1")).thenReturn(1);

        when(auctionRepositoryPort.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auctionRepositoryPort.existsActiveOrPendingForProduct(any())).thenReturn(false);
        when(auctionRepositoryPort.countActiveOrPendingBySeller(any())).thenReturn(0L);
    }

    private CreateAuctionCommand validCommand() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        return new CreateAuctionCommand("product-1", "seller-1", new BigDecimal("100.00"),
                new BigDecimal("10.00"), start, start.plus(2, ChronoUnit.HOURS), "TRUSTED");
    }

    @Test
    void create_savesAuctionAndPublishesEvent() {
        AuctionResult result = useCase.create(validCommand());

        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.productId()).isEqualTo("product-1");
        verify(eventPublisherPort).publish(any());
    }

    @Test
    void create_rejectsEndTimeBeforeStartTime() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        CreateAuctionCommand invalid = new CreateAuctionCommand("product-1", "seller-1",
                new BigDecimal("100.00"), new BigDecimal("10.00"), start, start.minus(1, ChronoUnit.HOURS), "TRUSTED");

        assertThatThrownBy(() -> useCase.create(invalid)).isInstanceOf(ValidationException.class);
        verify(auctionRepositoryPort, never()).save(any());
    }

    @Test
    void create_rejectsDurationBelowMinimum() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        CreateAuctionCommand tooShort = new CreateAuctionCommand("product-1", "seller-1",
                new BigDecimal("100.00"), new BigDecimal("10.00"), start, start.plus(30, ChronoUnit.MINUTES), "TRUSTED");

        assertThatThrownBy(() -> useCase.create(tooShort)).isInstanceOf(ValidationException.class);
    }

    @Test
    void create_rejectsDurationAboveMaximum() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        CreateAuctionCommand tooLong = new CreateAuctionCommand("product-1", "seller-1",
                new BigDecimal("100.00"), new BigDecimal("10.00"), start, start.plus(169, ChronoUnit.HOURS), "TRUSTED");

        assertThatThrownBy(() -> useCase.create(tooLong)).isInstanceOf(ValidationException.class);
    }

    @Test
    void create_rejectsWhenProductAlreadyHasAnActiveOrPendingAuction() {
        when(auctionRepositoryPort.existsActiveOrPendingForProduct("product-1")).thenReturn(true);

        assertThatThrownBy(() -> useCase.create(validCommand())).isInstanceOf(ConflictException.class);
        verify(auctionRepositoryPort, never()).save(any());
    }

    @Test
    void create_rejectsWhenSellerAtMaxActiveAuctions() {
        when(auctionRepositoryPort.countActiveOrPendingBySeller("seller-1")).thenReturn(5L);

        assertThatThrownBy(() -> useCase.create(validCommand())).isInstanceOf(ConflictException.class);
        verify(auctionRepositoryPort, never()).save(any());
    }

    @Test
    void create_rejectsSellerBelowMinReputationToSell() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        CreateAuctionCommand command = new CreateAuctionCommand("product-1", "seller-1",
                new BigDecimal("100.00"), new BigDecimal("10.00"), start, start.plus(2, ChronoUnit.HOURS), "NORMAL");

        assertThatThrownBy(() -> useCase.create(command)).isInstanceOf(ForbiddenException.class);
        verify(auctionRepositoryPort, never()).save(any());
    }

    @Test
    void create_allowsSellerWhenTrustLevelIsMissing() {
        // A null trustLevel (old token predating the claim, or a caller path that never set
        // it) fails open rather than blocking every seller.
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        CreateAuctionCommand command = new CreateAuctionCommand("product-1", "seller-1",
                new BigDecimal("100.00"), new BigDecimal("10.00"), start, start.plus(2, ChronoUnit.HOURS), null);

        AuctionResult result = useCase.create(command);

        assertThat(result.status()).isEqualTo("PENDING");
    }

    @Test
    void create_rejectsAProductAuctionDoesNotKnow() {
        when(productSnapshotRepositoryPort.findById("product-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.create(validCommand()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_NOT_AVAILABLE");
        verify(auctionRepositoryPort, never()).save(any());
    }

    @Test
    void create_rejectsAProductThatIsNotActive() {
        when(productSnapshotRepositoryPort.findById("product-1"))
                .thenReturn(Optional.of(AuctionProduct.create("product-1", "seller-1")));

        assertThatThrownBy(() -> useCase.create(validCommand()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_NOT_AVAILABLE");
        verify(auctionRepositoryPort, never()).save(any());
    }

    @Test
    void create_rejectsASellerAuctioningSomeoneElsesProduct() {
        when(productSnapshotRepositoryPort.findById("product-1"))
                .thenReturn(Optional.of(AuctionProduct.create("product-1", "seller-2").withStatus("ACTIVE")));

        assertThatThrownBy(() -> useCase.create(validCommand()))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("errorCode", "NOT_THE_PRODUCT_OWNER");
        verify(auctionRepositoryPort, never()).save(any());
        verify(eventPublisherPort, never()).publish(any());
    }

    @Test
    void create_rejectsAProductWithNoStockLeft() {
        when(productStockRepositoryPort.availableQuantity("product-1")).thenReturn(0);

        assertThatThrownBy(() -> useCase.create(validCommand()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INSUFFICIENT_STOCK");
        verify(auctionRepositoryPort, never()).save(any());
    }
}
