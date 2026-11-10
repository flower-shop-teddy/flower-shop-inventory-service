package com.flowershop.inventory.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Cấu hình inventory.* trong application.yml. Sai cấu hình thì service không khởi động (fail fast). */
@Validated
@ConfigurationProperties(prefix = "inventory")
public record InventoryProperties(
        @Valid @NotNull Reservation reservation,
        @Valid @NotNull Catalog catalog
) {
    /**
     * @param defaultHold  giữ hàng bao lâu nếu đơn không nói rõ (vd chờ khách thanh toán)
     * @param expiryBatch  mỗi lần job chạy nhả tối đa bao nhiêu phiếu quá hạn
     */
    public record Reservation(@NotNull Duration defaultHold, @Min(1) @Max(1000) int expiryBatch) {
    }

    public record Catalog(@NotBlank String baseUrl, @NotNull Duration connectTimeout, @NotNull Duration readTimeout) {
    }
}
