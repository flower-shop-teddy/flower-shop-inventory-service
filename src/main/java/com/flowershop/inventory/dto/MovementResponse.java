package com.flowershop.inventory.dto;

import com.flowershop.inventory.domain.MovementType;

import java.time.Instant;

public record MovementResponse(MovementType type, int quantityDelta, String reference, String actor,
                               Instant occurredAt) {
}
