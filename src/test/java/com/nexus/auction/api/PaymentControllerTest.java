package com.nexus.auction.api;

import com.nexus.auction.api.mapper.AuctionApiMapperImpl;
import com.nexus.auction.application.port.out.PaymentGatewayPort;
import com.nexus.auction.application.usecase.AuctionResult;
import com.nexus.auction.application.usecase.ConfirmPaymentUseCase;
import com.nexus.auction.application.usecase.CreateCheckoutSessionUseCase;
import com.nexus.auction.infrastructure.config.SecurityConfig;
import com.nexus.common.core.exception.ConflictException;
import com.nexus.common.core.exception.ForbiddenException;
import com.nexus.common.security.JwtAuthenticationFilter;
import com.nexus.common.security.JwtTokenProvider;
import com.nexus.common.security.PrivilegeAuthorizationAspect;
import com.nexus.common.web.GlobalExceptionHandler;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({GlobalExceptionHandler.class, AuctionApiMapperImpl.class, SecurityConfig.class,
        JwtAuthenticationFilter.class, PrivilegeAuthorizationAspect.class})
class PaymentControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private CreateCheckoutSessionUseCase createCheckoutSessionUseCase;
    @MockBean private ConfirmPaymentUseCase confirmPaymentUseCase;
    @MockBean private JwtTokenProvider jwtTokenProvider;

    private void authenticateAs(String subject, String... privileges) {
        when(jwtTokenProvider.isValid("good-token")).thenReturn(true);
        Claims claims = Jwts.claims().subject(subject).add("privileges", List.of(privileges)).build();
        when(jwtTokenProvider.parseClaims("good-token")).thenReturn(claims);
    }

    private AuctionResult paidResult() {
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS);
        return new AuctionResult("auction-1", "product-1", "seller-1", new BigDecimal("100.00"),
                new BigDecimal("10.00"), new BigDecimal("150.00"), "winner-1", "ENDED", start,
                start.plus(1, ChronoUnit.HOURS), 0, "winner-1", new BigDecimal("150.00"),
                start.plus(24, ChronoUnit.HOURS), Instant.now());
    }

    @Test
    void createCheckoutSession_returns401WithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/auctions/auction-1/checkout-session")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"successUrl":"https://fe/success","cancelUrl":"https://fe/cancel"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createCheckoutSession_returns403WithoutPrivilege() throws Exception {
        authenticateAs("winner-1");

        mockMvc.perform(post("/api/v1/auctions/auction-1/checkout-session")
                        .header("Authorization", "Bearer good-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"successUrl":"https://fe/success","cancelUrl":"https://fe/cancel"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCheckoutSession_returns200AndUsesJwtSubjectAsCaller() throws Exception {
        authenticateAs("winner-1", "AUCTION.BID");
        when(createCheckoutSessionUseCase.createSession("auction-1", "winner-1",
                "https://fe/success", "https://fe/cancel"))
                .thenReturn(new PaymentGatewayPort.CheckoutSession("sess-1", "https://checkout.stripe.com/sess-1"));

        mockMvc.perform(post("/api/v1/auctions/auction-1/checkout-session")
                        .header("Authorization", "Bearer good-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"successUrl":"https://fe/success","cancelUrl":"https://fe/cancel"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkoutUrl").value("https://checkout.stripe.com/sess-1"));
    }

    @Test
    void createCheckoutSession_returns409WhenNotWinner() throws Exception {
        authenticateAs("someone-else", "AUCTION.BID");
        when(createCheckoutSessionUseCase.createSession(eq("auction-1"), eq("someone-else"), any(), any()))
                .thenThrow(new ForbiddenException("NOT_THE_WINNER", "Only the winning bidder can pay"));

        mockMvc.perform(post("/api/v1/auctions/auction-1/checkout-session")
                        .header("Authorization", "Bearer good-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"successUrl":"https://fe/success","cancelUrl":"https://fe/cancel"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmPayment_returns200AndUsesJwtSubjectAsCaller() throws Exception {
        authenticateAs("winner-1", "AUCTION.BID");
        when(confirmPaymentUseCase.confirm("auction-1", "winner-1", "sess-1")).thenReturn(paidResult());

        mockMvc.perform(post("/api/v1/auctions/auction-1/confirm-payment")
                        .header("Authorization", "Bearer good-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"sessionId":"sess-1"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paidAt").exists());

        verify(confirmPaymentUseCase).confirm(eq("auction-1"), eq("winner-1"), eq("sess-1"));
    }

    @Test
    void confirmPayment_returns409WhenStripeHasNotConfirmedPayment() throws Exception {
        authenticateAs("winner-1", "AUCTION.BID");
        when(confirmPaymentUseCase.confirm("auction-1", "winner-1", "sess-1"))
                .thenThrow(new ConflictException("PAYMENT_NOT_CONFIRMED", "Stripe has not confirmed this payment yet"));

        mockMvc.perform(post("/api/v1/auctions/auction-1/confirm-payment")
                        .header("Authorization", "Bearer good-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"sessionId":"sess-1"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_NOT_CONFIRMED"));
    }
}
