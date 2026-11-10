package com.flowershop.inventory.service;

import com.flowershop.common.error.BusinessException;
import com.flowershop.inventory.config.InventoryProperties;
import com.flowershop.inventory.domain.MovementType;
import com.flowershop.inventory.domain.ReservationStatus;
import com.flowershop.inventory.domain.StockLot;
import com.flowershop.inventory.domain.StockReservation;
import com.flowershop.inventory.domain.StockReservationLine;
import com.flowershop.inventory.dto.ReservationResponse;
import com.flowershop.inventory.dto.ReserveRequest;
import com.flowershop.inventory.error.InventoryErrorCode;
import com.flowershop.inventory.repository.StockLotRepository;
import com.flowershop.inventory.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Giữ / xác nhận / nhả hàng cho đơn (chỉ order-service gọi).
 *
 * Giữ hàng là TẤT CẢ hoặc KHÔNG: thiếu một SKU thì cả transaction rollback, không giữ nửa đơn.
 * Khóa lô theo thứ tự SKU tăng dần: hai đơn cùng chứa SKU A và B luôn khóa A trước B,
 * nên không bao giờ đơn 1 giữ A chờ B trong khi đơn 2 giữ B chờ A (deadlock).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    /** Kết quả giữ hàng. created = false: gọi lại cùng orderCode, trả kết quả cũ (idempotent). */
    public record ReserveResult(ReservationResponse reservation, boolean created) {
    }

    private final StockReservationRepository reservations;
    private final StockLotRepository lots;
    private final MovementRecorder recorder;
    private final InventoryProperties props;
    private final Clock clock;

    @Transactional
    public ReserveResult reserve(ReserveRequest req) {
        Instant now = clock.instant();
        Map<String, Integer> wanted = toSortedMap(req.items());

        var existing = reservations.findByOrderCode(req.orderCode());
        if (existing.isPresent()) {
            if (!existing.get().quantitiesBySku().equals(wanted)) {
                throw new BusinessException(InventoryErrorCode.ORDER_CODE_REUSED);
            }
            return new ReserveResult(InventoryMapper.toResponse(existing.get()), false);
        }

        Duration hold = req.holdMinutes() == null ? props.reservation().defaultHold()
                : Duration.ofMinutes(req.holdMinutes());
        Instant freshUntil = req.freshUntil() == null || req.freshUntil().isBefore(now) ? now : req.freshUntil();
        StockReservation reservation = StockReservation.hold(req.orderCode(), now.plus(hold), freshUntil);
        try {
            // Ghi phiếu TRƯỚC khi khóa lô: hai request cùng orderCode đến cùng lúc thì request sau
            // dừng ở UNIQUE(order_code) ngay, không đi khóa lô vô ích
            reservations.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(InventoryErrorCode.RESERVATION_IN_PROGRESS);
        }

        wanted.forEach((sku, quantity) -> takeFefo(reservation, sku, quantity, freshUntil));
        log.info("Reserved order={} items={} holdUntil={}", req.orderCode(), wanted, reservation.getHoldUntil());
        return new ReserveResult(InventoryMapper.toResponse(reservation), true);
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(String orderCode) {
        return InventoryMapper.toResponse(reservations.findByOrderCode(orderCode)
                .orElseThrow(() -> new BusinessException(InventoryErrorCode.RESERVATION_NOT_FOUND)));
    }

    /** Đơn đã đặt thành công: giữ tới khi giao, job không tự nhả nữa. */
    @Transactional
    public ReservationResponse confirm(String orderCode) {
        StockReservation r = lockOrThrow(orderCode);
        if (r.confirm(clock.instant())) {
            log.info("Confirmed reservation order={}", orderCode);
        }
        return InventoryMapper.toResponse(r);
    }

    /** Đơn hủy: trả hàng về đúng lô đã lấy. Gọi lại nhiều lần chỉ trả kho một lần. */
    @Transactional
    public ReservationResponse release(String orderCode) {
        StockReservation r = lockOrThrow(orderCode);
        if (r.release(ReservationStatus.RELEASED, clock.instant())) {
            giveBack(r, MovementType.RELEASE);
            log.info("Released reservation order={}", orderCode);
        }
        return InventoryMapper.toResponse(r);
    }

    /** Job: nhả các phiếu HELD quá hold_until. @return số phiếu đã nhả */
    @Transactional
    public int expireOverdue(Instant now) {
        List<StockReservation> overdue = reservations.findOverdueForUpdate(now, props.reservation().expiryBatch());
        int count = 0;
        for (StockReservation r : overdue) {
            if (r.release(ReservationStatus.EXPIRED, now)) {
                giveBack(r, MovementType.EXPIRE_HOLD);
                count++;
            }
        }
        if (count > 0) {
            log.info("Expired {} overdue reservations", count);
        }
        return count;
    }

    private void takeFefo(StockReservation reservation, String sku, int quantity, Instant freshUntil) {
        int remaining = quantity;
        for (StockLot lot : lots.findFefoForUpdate(sku, freshUntil)) {
            int taken = lot.take(remaining);
            if (taken > 0) {
                reservation.addLine(lot, taken);
                recorder.record(lot, MovementType.RESERVE, -taken, reservation.getOrderCode());
                remaining -= taken;
            }
            if (remaining == 0) {
                return;
            }
        }
        // Ném lỗi -> rollback MỌI thứ đã lấy trong transaction này (cả các SKU trước đó)
        throw new BusinessException(InventoryErrorCode.INSUFFICIENT_STOCK,
                "Không đủ hàng cho SKU %s: cần %d, còn %d".formatted(sku, quantity, quantity - remaining));
    }

    private void giveBack(StockReservation r, MovementType type) {
        List<Long> lotIds = r.getLines().stream().map(l -> l.getLot().getId()).distinct().toList();
        Map<Long, StockLot> locked = lots.findAllByIdForUpdate(lotIds).stream()
                .collect(Collectors.toMap(StockLot::getId, Function.identity()));
        for (StockReservationLine line : r.getLines()) {
            StockLot lot = locked.get(line.getLot().getId());
            lot.giveBack(line.getQuantity());
            recorder.record(lot, type, line.getQuantity(), r.getOrderCode());
        }
    }

    private StockReservation lockOrThrow(String orderCode) {
        return reservations.findByOrderCodeForUpdate(orderCode)
                .orElseThrow(() -> new BusinessException(InventoryErrorCode.RESERVATION_NOT_FOUND));
    }

    private static Map<String, Integer> toSortedMap(List<ReserveRequest.Item> items) {
        Map<String, Integer> result = new TreeMap<>();   // TreeMap: duyệt theo SKU tăng dần (thứ tự khóa cố định)
        for (ReserveRequest.Item item : items) {
            if (result.put(item.sku(), item.quantity()) != null) {
                throw new BusinessException(InventoryErrorCode.DUPLICATE_SKU_IN_REQUEST);
            }
        }
        return result;
    }
}
