package com.flowershop.inventory.domain;

import com.flowershop.common.error.BusinessException;
import com.flowershop.inventory.error.InventoryErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Giữ hàng cho MỘT đơn. Chuyển trạng thái chỉ qua các method bên dưới (xem {@link ReservationStatus}). */
@Entity
@Table(name = "stock_reservation")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_code", nullable = false, unique = true, length = 30, updatable = false)
    private String orderCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "hold_until", nullable = false)
    private Instant holdUntil;

    @Column(name = "fresh_until", nullable = false, updatable = false)
    private Instant freshUntil;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<StockReservationLine> lines = new ArrayList<>();

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

    public static StockReservation hold(String orderCode, Instant holdUntil, Instant freshUntil) {
        StockReservation r = new StockReservation();
        r.orderCode = orderCode;
        r.status = ReservationStatus.HELD;
        r.holdUntil = holdUntil;
        r.freshUntil = freshUntil;
        return r;
    }

    public void addLine(StockLot lot, int quantity) {
        lines.add(StockReservationLine.of(this, lot, quantity));
    }

    /** Tổng số lượng theo SKU (một SKU có thể trải trên nhiều lô). */
    public Map<String, Integer> quantitiesBySku() {
        Map<String, Integer> result = new TreeMap<>();
        lines.forEach(l -> result.merge(l.getSku(), l.getQuantity(), Integer::sum));
        return result;
    }

    /** @return false nếu đã CONFIRMED rồi (gọi lại không lỗi: idempotent) */
    public boolean confirm(Instant now) {
        if (status == ReservationStatus.CONFIRMED) {
            return false;
        }
        if (status != ReservationStatus.HELD) {
            throw new BusinessException(InventoryErrorCode.RESERVATION_NOT_ACTIVE);
        }
        status = ReservationStatus.CONFIRMED;
        confirmedAt = now;
        return true;
    }

    /** @return false nếu hàng đã được trả rồi (RELEASED/EXPIRED): gọi lại không trả kho lần hai */
    public boolean release(ReservationStatus target, Instant now) {
        if (!status.holdsStock()) {
            return false;
        }
        // Đơn đã xác nhận thì giữ tới khi giao: chỉ được nhả chủ động (đơn hủy), không bao giờ tự hết hạn
        if (target == ReservationStatus.EXPIRED && status != ReservationStatus.HELD) {
            return false;
        }
        status = target;
        releasedAt = now;
        return true;
    }
}
