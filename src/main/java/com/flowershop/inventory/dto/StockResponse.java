package com.flowershop.inventory.dto;

/**
 * @param sellable số còn bán được (lô còn hạn)
 * @param expired  số còn trong lô ĐÃ hết hạn: cần ghi nhận hủy (waste) để kho khớp thực tế
 */
public record StockResponse(String sku, long sellable, long expired) {
}
