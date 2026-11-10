package com.flowershop.inventory.error;

import com.flowershop.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum InventoryErrorCode implements ErrorCode {
    INVALID_EXPIRY("INV-400-001", HttpStatus.BAD_REQUEST, "Hạn dùng phải sau thời điểm nhập"),
    DUPLICATE_SKU_IN_REQUEST("INV-400-002", HttpStatus.BAD_REQUEST, "Mỗi SKU chỉ được xuất hiện một lần"),
    INVALID_HOLD_DURATION("INV-400-003", HttpStatus.BAD_REQUEST, "Thời gian giữ hàng không hợp lệ"),
    LOT_NOT_FOUND("INV-404-001", HttpStatus.NOT_FOUND, "Không tìm thấy lô hàng"),
    RESERVATION_NOT_FOUND("INV-404-002", HttpStatus.NOT_FOUND, "Không tìm thấy phiếu giữ hàng"),
    INSUFFICIENT_STOCK("INV-409-001", HttpStatus.CONFLICT, "Không đủ hàng"),
    ORDER_CODE_REUSED("INV-409-002", HttpStatus.CONFLICT, "Mã đơn đã giữ hàng với danh sách sản phẩm khác"),
    RESERVATION_NOT_ACTIVE("INV-409-003", HttpStatus.CONFLICT, "Phiếu giữ hàng đã được nhả hoặc hết hạn"),
    RESERVATION_IN_PROGRESS("INV-409-004", HttpStatus.CONFLICT, "Đơn này đang được giữ hàng, vui lòng thử lại"),
    SKU_NOT_IN_CATALOG("INV-422-001", HttpStatus.UNPROCESSABLE_ENTITY, "SKU không có trong danh mục sản phẩm"),
    SKU_INACTIVE("INV-422-002", HttpStatus.UNPROCESSABLE_ENTITY, "Sản phẩm đã ngừng kinh doanh"),
    WASTE_EXCEEDS_AVAILABLE("INV-422-003", HttpStatus.UNPROCESSABLE_ENTITY, "Số lượng hủy lớn hơn số còn trong lô"),
    CATALOG_UNAVAILABLE("INV-503-001", HttpStatus.SERVICE_UNAVAILABLE,
            "Chưa kiểm tra được sản phẩm, vui lòng thử lại sau");

    private final String code;
    private final HttpStatus status;
    private final String defaultMessage;

    InventoryErrorCode(String code, HttpStatus status, String defaultMessage) {
        this.code = code;
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    @Override public String code() { return code; }
    @Override public HttpStatus status() { return status; }
    @Override public String defaultMessage() { return defaultMessage; }
}
