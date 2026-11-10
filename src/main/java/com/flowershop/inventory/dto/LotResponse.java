package com.flowershop.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record LotResponse(
        String lotCode,
        String sku,
        int qtyReceived,
        int qtyAvailable,
        BigDecimal unitCost,
        String currency,
        String supplier,
        Instant receivedAt,
        Instant expiresAt,
        boolean expired
) {
}
