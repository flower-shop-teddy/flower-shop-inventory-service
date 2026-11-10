package com.flowershop.inventory.service;

import com.flowershop.inventory.domain.StockLot;
import com.flowershop.inventory.domain.StockMovement;
import com.flowershop.inventory.domain.StockReservation;
import com.flowershop.inventory.dto.LotResponse;
import com.flowershop.inventory.dto.MovementResponse;
import com.flowershop.inventory.dto.ReservationResponse;

import java.time.Instant;

final class InventoryMapper {

    private InventoryMapper() {
    }

    static LotResponse toResponse(StockLot lot, Instant now) {
        return new LotResponse(lot.getLotCode(), lot.getSku(), lot.getQtyReceived(), lot.getQtyAvailable(),
                lot.getUnitCost(), lot.getCurrency(), lot.getSupplier(), lot.getReceivedAt(), lot.getExpiresAt(),
                lot.isExpired(now));
    }

    static MovementResponse toResponse(StockMovement m) {
        return new MovementResponse(m.getMovementType(), m.getQuantityDelta(), m.getReference(), m.getActor(),
                m.getOccurredAt());
    }

    static ReservationResponse toResponse(StockReservation r) {
        return new ReservationResponse(r.getOrderCode(), r.getStatus(), r.getHoldUntil(), r.getFreshUntil(),
                r.getLines().stream()
                        .map(l -> new ReservationResponse.Line(l.getSku(), l.getLot().getLotCode(), l.getQuantity(),
                                l.getLot().getExpiresAt()))
                        .toList());
    }
}
