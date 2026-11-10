package com.flowershop.inventory.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** Nhập một lô hoa. Thời điểm nhập (received_at) lấy theo giờ server, không tin giờ của client. */
public record ReceiveLotRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9-]{3,30}$", message = "SKU chỉ gồm chữ IN HOA, số, dấu '-', dài 3-30 ký tự")
        String sku,
        @NotNull @Min(1) @Max(100_000) Integer quantity,
        @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal unitCost,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "Mã tiền tệ theo ISO 4217, ví dụ VND") String currency,
        @NotNull Instant expiresAt,
        @Size(max = 150) String supplier
) {
}
