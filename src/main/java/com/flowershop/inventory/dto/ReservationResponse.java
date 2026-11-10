package com.flowershop.inventory.dto;

import com.flowershop.inventory.domain.ReservationStatus;

import java.time.Instant;
import java.util.List;

public record ReservationResponse(
        String orderCode,
        ReservationStatus status,
        Instant holdUntil,
        Instant freshUntil,
        List<Line> lines
) {
    public record Line(String sku, String lotCode, int quantity, Instant lotExpiresAt) {
    }
}
