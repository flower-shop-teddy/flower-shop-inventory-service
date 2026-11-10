package com.flowershop.inventory.domain;

import com.flowershop.common.error.BusinessException;
import com.flowershop.inventory.error.InventoryErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

/** Một lần nhập hoa: cùng SKU, cùng ngày hết hạn. Số lượng chỉ đổi qua các method bên dưới (kèm sổ cái). */
@Entity
@Table(name = "stock_lot")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockLot {

    public static final long MAX_CODE_NUMBER = 9_999_999L;   // khớp MAXVALUE của lot_code_seq

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lot_code", nullable = false, unique = true, length = 20, updatable = false)
    private String lotCode;

    @Column(nullable = false, length = 30, updatable = false)
    private String sku;

    @Column(name = "qty_received", nullable = false, updatable = false)
    private int qtyReceived;

    @Column(name = "qty_available", nullable = false)
    private int qtyAvailable;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal unitCost;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(length = 150, updatable = false)
    private String supplier;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    public static StockLot receive(long codeNumber, String sku, int quantity, BigDecimal unitCost, String currency,
                                   String supplier, Instant receivedAt, Instant expiresAt) {
        StockLot lot = new StockLot();
        lot.lotCode = "LO_%07d".formatted(codeNumber);
        lot.sku = sku;
        lot.qtyReceived = quantity;
        lot.qtyAvailable = quantity;
        lot.unitCost = unitCost;
        lot.currency = currency;
        lot.supplier = supplier;
        lot.receivedAt = receivedAt;
        lot.expiresAt = expiresAt;
        return lot;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    /** Lấy tối đa {@code wanted}, trả về số thực lấy được (lô không đủ thì lấy hết lô). */
    public int take(int wanted) {
        int taken = Math.min(wanted, qtyAvailable);
        qtyAvailable -= taken;
        return taken;
    }

    /** Trả lại hàng đã giữ (đơn hủy, hết thời gian giữ). */
    public void giveBack(int quantity) {
        if (qtyAvailable + quantity > qtyReceived) {
            // Không bao giờ xảy ra nếu sổ cái đúng: dừng lại còn hơn ghi sai kho
            throw new IllegalStateException("Trả lại vượt số đã nhập: lot=" + lotCode);
        }
        qtyAvailable += quantity;
    }

    public void waste(int quantity) {
        if (quantity > qtyAvailable) {
            throw new BusinessException(InventoryErrorCode.WASTE_EXCEEDS_AVAILABLE);
        }
        qtyAvailable -= quantity;
    }
}
