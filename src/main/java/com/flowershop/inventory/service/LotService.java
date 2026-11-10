package com.flowershop.inventory.service;

import com.flowershop.common.api.PageResponse;
import com.flowershop.common.error.BusinessException;
import com.flowershop.inventory.client.CatalogClient;
import com.flowershop.inventory.domain.MovementType;
import com.flowershop.inventory.domain.StockLot;
import com.flowershop.inventory.dto.AvailabilityResponse;
import com.flowershop.inventory.dto.LotResponse;
import com.flowershop.inventory.dto.MovementResponse;
import com.flowershop.inventory.dto.ReceiveLotRequest;
import com.flowershop.inventory.dto.StockResponse;
import com.flowershop.inventory.dto.WasteRequest;
import com.flowershop.inventory.error.InventoryErrorCode;
import com.flowershop.inventory.repository.StockLotRepository;
import com.flowershop.inventory.repository.StockMovementRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Nhập lô, ghi nhận hoa hủy, xem tồn kho. */
@Slf4j
@Service
public class LotService {

    private static final int MAX_PAGE_SIZE = 100;

    private final StockLotRepository lots;
    private final StockMovementRepository movements;
    private final MovementRecorder recorder;
    private final CatalogClient catalog;
    private final Clock clock;
    private final TransactionTemplate tx;

    public LotService(StockLotRepository lots, StockMovementRepository movements, MovementRecorder recorder,
                      CatalogClient catalog, Clock clock, PlatformTransactionManager txManager) {
        this.lots = lots;
        this.movements = movements;
        this.recorder = recorder;
        this.catalog = catalog;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * Gọi catalog TRƯỚC, rồi mới mở transaction: không giữ kết nối DB trong lúc chờ service khác
     * (catalog chậm 5 giây mà đang giữ connection thì vài chục request là cạn pool).
     */
    public LotResponse receive(ReceiveLotRequest req) {
        Instant now = clock.instant();
        if (!req.expiresAt().isAfter(now)) {
            throw new BusinessException(InventoryErrorCode.INVALID_EXPIRY);
        }
        catalog.requireActive(req.sku());

        return tx.execute(status -> {
            long number = lots.nextLotCodeNumber();
            StockLot lot = lots.saveAndFlush(StockLot.receive(number, req.sku(), req.quantity(), req.unitCost(),
                    req.currency(), blankToNull(req.supplier()), now, req.expiresAt()));
            recorder.record(lot, MovementType.RECEIVE, lot.getQtyReceived(), null);
            log.info("Received lot={} sku={} qty={} expiresAt={}", lot.getLotCode(), lot.getSku(),
                    lot.getQtyReceived(), lot.getExpiresAt());
            return InventoryMapper.toResponse(lot, now);
        });
    }

    @Transactional
    public LotResponse waste(String lotCode, WasteRequest req) {
        StockLot lot = lots.findByLotCodeForUpdate(lotCode)
                .orElseThrow(() -> new BusinessException(InventoryErrorCode.LOT_NOT_FOUND));
        lot.waste(req.quantity());
        String reference = req.reason() + (req.note() == null || req.note().isBlank() ? "" : ": " + req.note().strip());
        recorder.record(lot, MovementType.WASTE, -req.quantity(), reference);
        log.info("Waste lot={} qty={} reason={}", lotCode, req.quantity(), req.reason());
        return InventoryMapper.toResponse(lot, clock.instant());
    }

    @Transactional(readOnly = true)
    public LotResponse getLot(String lotCode) {
        return InventoryMapper.toResponse(findOrThrow(lotCode), clock.instant());
    }

    @Transactional(readOnly = true)
    public List<MovementResponse> movements(String lotCode) {
        StockLot lot = findOrThrow(lotCode);
        return movements.findByLotIdOrderByIdAsc(lot.getId()).stream().map(InventoryMapper::toResponse).toList();
    }

    /** Mặc định chỉ lô còn hàng, sắp theo hạn dùng (lô sắp hết hạn lên đầu: nhân viên biết bán/hủy trước). */
    @Transactional(readOnly = true)
    public PageResponse<LotResponse> listLots(String sku, boolean includeEmpty, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by("expiresAt", "id"));
        Page<StockLot> result = includeEmpty
                ? lots.findBySku(sku, pageable)
                : lots.findBySkuAndQtyAvailableGreaterThan(sku, 0, pageable);
        Instant now = clock.instant();
        return new PageResponse<>(result.getContent().stream().map(l -> InventoryMapper.toResponse(l, now)).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public StockResponse stock(String sku) {
        Instant now = clock.instant();
        return new StockResponse(sku, lots.sumSellable(sku, now), lots.sumExpired(sku, now));
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse availability(String sku) {
        return new AvailabilityResponse(sku, lots.sumSellable(sku, clock.instant()) > 0);
    }

    private StockLot findOrThrow(String lotCode) {
        return lots.findByLotCode(lotCode).orElseThrow(() -> new BusinessException(InventoryErrorCode.LOT_NOT_FOUND));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
