package com.flowershop.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WasteRequest(
        @NotNull @Min(1) @Max(100_000) Integer quantity,
        @NotNull WasteReason reason,
        @Size(max = 80) String note
) {
    public enum WasteReason { EXPIRED, DAMAGED, OTHER }
}
