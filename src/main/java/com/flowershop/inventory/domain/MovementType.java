package com.flowershop.inventory.domain;

public enum MovementType {
    /** Nhập lô mới (+) */
    RECEIVE,
    /** Giữ hàng cho đơn (-) */
    RESERVE,
    /** Đơn hủy, trả lại hàng (+) */
    RELEASE,
    /** Quá thời gian giữ mà đơn chưa xác nhận, job tự trả lại (+) */
    EXPIRE_HOLD,
    /** Hoa hỏng, héo, hết hạn phải hủy (-) */
    WASTE
}
