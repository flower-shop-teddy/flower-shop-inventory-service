package com.flowershop.inventory.dto;

/** Cho khách xem: chỉ còn hay hết, không lộ số lượng tồn kho (thông tin kinh doanh). */
public record AvailabilityResponse(String sku, boolean available) {
}
