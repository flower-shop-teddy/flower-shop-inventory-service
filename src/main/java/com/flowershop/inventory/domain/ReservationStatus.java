package com.flowershop.inventory.domain;

/**
 * HELD ──confirm──► CONFIRMED
 *  │                   │
 *  ├──release──► RELEASED ◄──release (đơn hủy trước khi giao)
 *  └──quá hold_until──► EXPIRED (job)
 * RELEASED / EXPIRED là trạng thái cuối: hàng đã trả lại kho.
 */
public enum ReservationStatus {
    HELD, CONFIRMED, RELEASED, EXPIRED;

    public boolean holdsStock() {
        return this == HELD || this == CONFIRMED;
    }
}
