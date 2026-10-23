package com.minhnbnt.fashionstore.features.admin;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.admin.AdminOrderDetailResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminOrderFilter;
import com.minhnbnt.fashionstore.dto.admin.AdminOrderResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminOrderSummaryResponse;
import com.minhnbnt.fashionstore.dto.admin.OrderTimelineResponse;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.model.AdminCancelReason;
import com.minhnbnt.fashionstore.model.OrderAlert;
import com.minhnbnt.fashionstore.model.OrderStatus;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminOrderController.class)
@Import(SecurityConfig.class)
class AdminOrderControllerTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AdminOrderService adminOrders;

    private static RequestPostProcessor admin() {
        return jwt()
            .jwt(builder -> builder.subject(ADMIN_ID.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static AdminOrderResponse order(UUID id, String status, @Nullable String alert) {
        var now = Instant.now();
        return new AdminOrderResponse(
            id, "ORD-AB12CD34", UUID.randomUUID(), "Shop An", "shop-an", UUID.randomUUID(), "buyer@t.local",
            "Nguyen An", "0901234567", status, "UNPAID", 200000L, 2, "Ao thun", alert, null, null, null, now, now
        );
    }

    @Test
    void listPassesAllFilters() throws Exception {
        var id = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var buyerId = UUID.randomUUID();
        var filter = new AdminOrderFilter(
            OrderStatus.PENDING,
            OrderAlert.CONFIRM_LATE,
            shopId,
            buyerId,
            "ORD",
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 18)
        );
        when(adminOrders.search(eq(filter), eq(1), eq(10)))
            .thenReturn(new PageResponse<>(List.of(order(id, "PENDING", "CONFIRM_LATE")), 11, 1, 10));

        mockMvc.perform(get("/api/admin/orders")
                .param("status", "PENDING")
                .param("alert", "CONFIRM_LATE")
                .param("shopId", shopId.toString())
                .param("buyerId", buyerId.toString())
                .param("q", "ORD")
                .param("from", "2026-10-01")
                .param("to", "2026-10-18")
                .param("page", "1")
                .param("size", "10")
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(11))
            .andExpect(jsonPath("$.items[0].alert").value("CONFIRM_LATE"))
            .andExpect(jsonPath("$.items[0].shopName").value("Shop An"));
    }

    @Test
    void listWithoutFiltersMeansAll() throws Exception {
        var filter = new AdminOrderFilter(null, null, null, null, null, null, null);
        when(adminOrders.search(eq(filter), eq(0), eq(20))).thenReturn(new PageResponse<>(List.of(), 0, 0, 20));

        mockMvc.perform(get("/api/admin/orders").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void listRejectsUnknownAlertAndOversizedPage() throws Exception {
        mockMvc.perform(get("/api/admin/orders").param("alert", "SLOW").with(admin()))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/orders").param("size", "500").with(admin()))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(adminOrders);
    }

    @Test
    void listNeedsAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/orders").with(jwt()))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/orders"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void summaryIgnoresStatusAndAlert() throws Exception {
        var shopId = UUID.randomUUID();
        var filter = new AdminOrderFilter(null, null, shopId, null, null, null, null);
        when(adminOrders.summary(filter)).thenReturn(new AdminOrderSummaryResponse(9, 3, 2, 1, 2, 1, 2, 1, 0, 1));

        mockMvc.perform(get("/api/admin/orders/summary").param("shopId", shopId.toString()).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(9))
            .andExpect(jsonPath("$.confirmLate").value(2))
            .andExpect(jsonPath("$.returnOpen").value(1));
    }

    @Test
    void detailReturnsTimeline() throws Exception {
        var id = UUID.randomUUID();
        var timeline = new OrderTimelineResponse(
            "PENDING", "CANCELLED", "ADMIN", ADMIN_ID, "admin@t.local", "Nghi don ao", Instant.now()
        );
        when(adminOrders.getDetail(id)).thenReturn(new AdminOrderDetailResponse(
            order(id, "CANCELLED", null), "APPROVED", "COD", 200000L, 0L, "Ha Noi", "", "Nghi don ao",
            null, null, null, Instant.now(), List.of(), List.of(timeline), List.of()
        ));

        mockMvc.perform(get("/api/admin/orders/{id}", id).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("CANCELLED"))
            .andExpect(jsonPath("$.timeline[0].actor").value("ADMIN"))
            .andExpect(jsonPath("$.timeline[0].changedByEmail").value("admin@t.local"));
    }

    @Test
    void cancelUsesAdminIdFromToken() throws Exception {
        var id = UUID.randomUUID();
        when(adminOrders.cancel(ADMIN_ID, id, AdminCancelReason.FRAUD_SUSPECTED, "Nghi don ao"))
            .thenReturn(order(id, "CANCELLED", null));

        mockMvc.perform(patch("/api/admin/orders/{id}/cancel", id)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"FRAUD_SUSPECTED\",\"note\":\"Nghi don ao\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelRequiresReasonAndNote() throws Exception {
        mockMvc.perform(patch("/api/admin/orders/{id}/cancel", UUID.randomUUID())
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"Nghi don ao\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/admin/orders/{id}/cancel", UUID.randomUUID())
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"OTHER\",\"note\":\"abc\"}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(adminOrders);
    }

    @Test
    void cancelNeedsAdmin() throws Exception {
        mockMvc.perform(patch("/api/admin/orders/{id}/cancel", UUID.randomUUID())
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"OTHER\",\"note\":\"Nghi don ao\"}"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(adminOrders);
    }
}
