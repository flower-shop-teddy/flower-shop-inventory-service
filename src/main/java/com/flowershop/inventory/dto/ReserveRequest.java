package com.flowershop.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Giữ hàng cho một đơn.
 *
 * @param orderCode   khóa idempotency: gọi lại cùng mã + cùng danh sách thì trả kết quả cũ, không giữ thêm
 * @param holdMinutes giữ trong bao lâu nếu đơn chưa xác nhận (null = mặc định trong cấu hình)
 * @param freshUntil  chỉ lấy lô còn hạn SAU thời điểm này, vd giờ giao hàng (null = bây giờ)
 */
public record ReserveRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9_-]{3,30}$") String orderCode,
        @NotEmpty @Size(max = 50) List<@Valid Item> items,
        @Min(1) @Max(1440) Integer holdMinutes,
        Instant freshUntil
) {
    public record Item(
            @NotBlank @Pattern(regexp = "^[A-Z0-9-]{3,30}$") String sku,
            @NotNull @Min(1) @Max(10_000) Integer quantity
    ) {
    }
}
