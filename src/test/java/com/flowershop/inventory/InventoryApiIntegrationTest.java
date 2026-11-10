package com.flowershop.inventory;

import com.flowershop.common.error.BusinessException;
import com.flowershop.inventory.client.CatalogClient;
import com.flowershop.inventory.dto.ReserveRequest;
import com.flowershop.inventory.error.InventoryErrorCode;
import com.flowershop.inventory.service.ReservationService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test tích hợp với Postgres thật (Testcontainers), cần Docker đang chạy.
 * catalog-service thay bằng mock (mặc định: SKU nào cũng hợp lệ). Job nhả hàng tắt, test gọi thẳng service.
 */
@SpringBootTest(properties = {
        "DB_URL=jdbc:postgresql://unused:5432/unused",
        "DB_USERNAME=unused",
        "DB_PASSWORD=unused",
        "eureka.client.enabled=false",
        "inventory.reservation.expiry-job-enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class InventoryApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReservationService reservationService;

    @MockitoBean
    CatalogClient catalog;

    // ---------------------------------------------------------------- tokens giả (bỏ qua chữ ký)

    private static RequestPostProcessor staff() {
        return jwt().jwt(j -> j.subject("staff-uuid").claim("user_type", "STAFF"))
                .authorities(new SimpleGrantedAuthority("inventory:read"), new SimpleGrantedAuthority("inventory:write"));
    }

    private static RequestPostProcessor orderService() {
        return jwt().jwt(j -> j.subject("order-service").claim("user_type", "SERVICE"))
                .authorities(new SimpleGrantedAuthority("inventory:reserve"));
    }

    private static RequestPostProcessor customer() {
        return jwt().jwt(j -> j.subject("customer-uuid").claim("user_type", "CUSTOMER"));
    }

    // ---------------------------------------------------------------- nhập lô

    @Test
    void receive_createsLotWithBusinessCode_andLedgerEntry() throws Exception {
        String sku = nextSku();
        String body = receive(sku, 10, Duration.ofDays(3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lotCode").value(org.hamcrest.Matchers.matchesPattern("LO_\\d{7}")))
                .andExpect(jsonPath("$.data.qtyAvailable").value(10))
                .andExpect(jsonPath("$.data.expired").value(false))
                .andReturn().getResponse().getContentAsString();
        String lotCode = JsonPath.read(body, "$.data.lotCode");

        mockMvc.perform(get("/api/v1/inventory/lots/" + lotCode + "/movements").with(staff()))
                .andExpect(jsonPath("$.data[0].type").value("RECEIVE"))
                .andExpect(jsonPath("$.data[0].quantityDelta").value(10))
                .andExpect(jsonPath("$.data[0].actor").value("staff-uuid"));
    }

    @Test
    void receive_rejectsUnknownSku_pastExpiry_andMissingPermission() throws Exception {
        String unknown = nextSku();
        willThrow(new BusinessException(InventoryErrorCode.SKU_NOT_IN_CATALOG)).given(catalog).requireActive(unknown);
        receive(unknown, 5, Duration.ofDays(1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INV-422-001"));

        receive(nextSku(), 5, Duration.ofHours(-1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INV-400-001"));

        // Khách không có inventory:write: chặn ở tầng URL, trước cả khi validate body
        mockMvc.perform(post("/api/v1/inventory/lots").with(customer()).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COM-403-001"));
    }

    // ---------------------------------------------------------------- FEFO

    @Test
    void reserve_takesEarliestExpiringLotFirst_thenSpillsToNext() throws Exception {
        String sku = nextSku();
        String later = lotCode(receive(sku, 10, Duration.ofDays(5)));
        String sooner = lotCode(receive(sku, 4, Duration.ofDays(1)));

        reserve(nextOrder(), Map.of(sku, 6), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("HELD"))
                .andExpect(jsonPath("$.data.lines.length()").value(2))
                .andExpect(jsonPath("$.data.lines[0].lotCode").value(sooner))
                .andExpect(jsonPath("$.data.lines[0].quantity").value(4))
                .andExpect(jsonPath("$.data.lines[1].lotCode").value(later))
                .andExpect(jsonPath("$.data.lines[1].quantity").value(2));

        assertSellable(sku, 8);
    }

    @Test
    void reserve_withFreshUntil_skipsLotsExpiringBeforeDelivery() throws Exception {
        String sku = nextSku();
        receive(sku, 10, Duration.ofDays(1));                       // hết hạn trước giờ giao: bỏ qua
        String fresh = lotCode(receive(sku, 10, Duration.ofDays(4)));

        reserve(nextOrder(), Map.of(sku, 3), Instant.now().plus(Duration.ofDays(2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lines[0].lotCode").value(fresh));
    }

    // ---------------------------------------------------------------- tất cả hoặc không

    @Test
    void reserve_isAllOrNothing_acrossSkus() throws Exception {
        String enough = nextSku();
        String scarce = nextSku();
        receive(enough, 10, Duration.ofDays(2));
        receive(scarce, 2, Duration.ofDays(2));

        reserve(nextOrder(), Map.of(enough, 5, scarce, 3), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INV-409-001"))
                .andExpect(jsonPath("$.message").value("Không đủ hàng cho SKU %s: cần 3, còn 2".formatted(scarce)));

        assertSellable(enough, 10);   // SKU đủ hàng cũng KHÔNG bị trừ
        assertSellable(scarce, 2);
    }

    // ---------------------------------------------------------------- idempotency

    @Test
    void reserve_sameOrderCode_isIdempotent_differentItemsIsRejected() throws Exception {
        String sku = nextSku();
        receive(sku, 10, Duration.ofDays(2));
        String order = nextOrder();

        reserve(order, Map.of(sku, 4), null).andExpect(status().isCreated());
        reserve(order, Map.of(sku, 4), null)
                .andExpect(status().isOk())   // 200: phiếu cũ, không giữ thêm
                .andExpect(jsonPath("$.data.lines[0].quantity").value(4));
        assertSellable(sku, 6);

        reserve(order, Map.of(sku, 5), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INV-409-002"));
    }

    // ---------------------------------------------------------------- vòng đời phiếu

    @Test
    void release_returnsStockOnce_andConfirmAfterReleaseIsRejected() throws Exception {
        String sku = nextSku();
        receive(sku, 10, Duration.ofDays(2));
        String order = nextOrder();
        reserve(order, Map.of(sku, 7), null).andExpect(status().isCreated());
        assertSellable(sku, 3);

        action(order, "release").andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RELEASED"));
        action(order, "release").andExpect(status().isOk());   // gọi lại: không trả kho lần hai
        assertSellable(sku, 10);

        action(order, "confirm")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INV-409-003"));
    }

    @Test
    void expiryJob_releasesOnlyOverdueHeld_notConfirmed() throws Exception {
        String sku = nextSku();
        receive(sku, 10, Duration.ofDays(3));
        String held = nextOrder();
        String confirmed = nextOrder();
        reserve(held, Map.of(sku, 3), null).andExpect(status().isCreated());
        reserve(confirmed, Map.of(sku, 2), null).andExpect(status().isCreated());
        action(confirmed, "confirm").andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        reservationService.expireOverdue(Instant.now().plus(Duration.ofHours(1)));

        mockMvc.perform(get("/internal/v1/reservations/" + held).with(orderService()))
                .andExpect(jsonPath("$.data.status").value("EXPIRED"));
        mockMvc.perform(get("/internal/v1/reservations/" + confirmed).with(orderService()))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        assertSellable(sku, 8);
    }

    // ---------------------------------------------------------------- tranh chấp

    @Test
    void concurrentReservations_neverOversell() throws Exception {
        String sku = nextSku();
        receive(sku, 10, Duration.ofDays(2));
        int threads = 20;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                String order = nextOrder();
                Callable<Boolean> task = () -> {
                    start.await();
                    try {
                        reservationService.reserve(new ReserveRequest(order,
                                List.of(new ReserveRequest.Item(sku, 1)), null, null));
                        return true;
                    } catch (BusinessException e) {
                        assertThat(e.getErrorCode()).isEqualTo(InventoryErrorCode.INSUFFICIENT_STOCK);
                        return false;
                    }
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            long succeeded = 0;
            for (Future<Boolean> f : results) {
                if (f.get()) {
                    succeeded++;
                }
            }
            assertThat(succeeded).isEqualTo(10);   // đúng bằng số hàng có, không hơn
        } finally {
            pool.shutdownNow();
        }
        assertSellable(sku, 0);
    }

    // ---------------------------------------------------------------- hủy hàng, tồn kho, đối soát

    @Test
    void waste_cannotExceedAvailable_andExpiredStockIsReportedSeparately() throws Exception {
        String sku = nextSku();
        String lot = lotCode(receive(sku, 6, Duration.ofDays(2)));
        waste(lot, 7).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INV-422-003"));
        waste(lot, 2).andExpect(status().isOk()).andExpect(jsonPath("$.data.qtyAvailable").value(4));

        // Giả lập lô đã quá hạn (lùi cả ngày nhập để không vi phạm CHECK expires_at > received_at)
        jdbc.update("UPDATE stock_lot SET received_at = now() - interval '3 days', expires_at = now() - interval '1 hour'"
                + " WHERE lot_code = ?", lot);
        mockMvc.perform(get("/api/v1/inventory/stock").param("sku", sku).with(staff()))
                .andExpect(jsonPath("$.data.sellable").value(0))
                .andExpect(jsonPath("$.data.expired").value(4));
        mockMvc.perform(get("/api/v1/inventory/availability").param("sku", sku))   // public, không token
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    void ledger_alwaysReconcilesWithLotQuantities() throws Exception {
        String sku = nextSku();
        String lot = lotCode(receive(sku, 20, Duration.ofDays(2)));
        String a = nextOrder();
        String b = nextOrder();
        reserve(a, Map.of(sku, 5), null);
        reserve(b, Map.of(sku, 3), null);
        action(a, "release");
        waste(lot, 4);
        reservationService.expireOverdue(Instant.now().plus(Duration.ofHours(1)));   // nhả b

        Integer mismatches = jdbc.queryForObject("""
                SELECT count(*) FROM stock_lot l
                WHERE l.qty_available <> (SELECT coalesce(sum(m.quantity_delta), 0) FROM stock_movement m WHERE m.lot_id = l.id)
                """, Integer.class);
        assertThat(mismatches).isZero();
        assertSellable(sku, 16);
    }

    @Test
    void reservationApis_requireReservePermission() throws Exception {
        mockMvc.perform(post("/internal/v1/reservations").with(staff()).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/v1/reservations").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions receive(String sku, int qty, Duration expiresIn) throws Exception {
        return mockMvc.perform(post("/api/v1/inventory/lots").with(staff()).contentType(APPLICATION_JSON).content("""
                {"sku":"%s","quantity":%d,"unitCost":12000,"currency":"VND","expiresAt":"%s","supplier":"Vườn Đà Lạt"}
                """.formatted(sku, qty, Instant.now().plus(expiresIn))));
    }

    private ResultActions reserve(String order, Map<String, Integer> items, Instant freshUntil) throws Exception {
        StringBuilder json = new StringBuilder("{\"orderCode\":\"").append(order).append("\",\"items\":[");
        items.forEach((sku, qty) -> json.append("{\"sku\":\"%s\",\"quantity\":%d},".formatted(sku, qty)));
        json.setLength(json.length() - 1);
        json.append("]");
        if (freshUntil != null) {
            json.append(",\"freshUntil\":\"").append(freshUntil).append('"');
        }
        json.append('}');
        return mockMvc.perform(post("/internal/v1/reservations").with(orderService())
                .contentType(APPLICATION_JSON).content(json.toString()));
    }

    private ResultActions action(String order, String action) throws Exception {
        return mockMvc.perform(post("/internal/v1/reservations/" + order + "/" + action).with(orderService()));
    }

    private ResultActions waste(String lot, int qty) throws Exception {
        return mockMvc.perform(post("/api/v1/inventory/lots/" + lot + "/waste").with(staff())
                .contentType(APPLICATION_JSON).content("{\"quantity\":%d,\"reason\":\"DAMAGED\",\"note\":\"dập\"}".formatted(qty)));
    }

    private void assertSellable(String sku, int expected) throws Exception {
        mockMvc.perform(get("/api/v1/inventory/stock").param("sku", sku).with(staff()))
                .andExpect(jsonPath("$.data.sellable").value(expected));
    }

    private static String lotCode(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.lotCode");
    }

    private static String nextSku() {
        return "SKU-T" + SEQ.incrementAndGet();
    }

    private static String nextOrder() {
        return "DH_T" + SEQ.incrementAndGet();
    }
}
