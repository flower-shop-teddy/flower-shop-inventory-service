package com.flowershop.inventory.repository;

import com.flowershop.inventory.domain.StockLot;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockLotRepository extends JpaRepository<StockLot, Long> {

    @Query(value = "SELECT nextval('lot_code_seq')", nativeQuery = true)
    long nextLotCodeNumber();

    Optional<StockLot> findByLotCode(String lotCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from StockLot l where l.lotCode = :lotCode")
    Optional<StockLot> findByLotCodeForUpdate(@Param("lotCode") String lotCode);

    /**
     * FEFO: lô còn hàng, còn hạn sau {@code freshUntil}, hết hạn sớm nhất đứng đầu.
     * SELECT ... FOR UPDATE: khóa các lô này tới hết transaction. Request giữ hàng thứ hai cho cùng SKU
     * phải ĐỢI request đầu xong mới đọc được qty_available mới: không bao giờ hai đơn lấy cùng một bông hoa.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from StockLot l
            where l.sku = :sku and l.qtyAvailable > 0 and l.expiresAt > :freshUntil
            order by l.expiresAt asc, l.id asc
            """)
    List<StockLot> findFefoForUpdate(@Param("sku") String sku, @Param("freshUntil") Instant freshUntil);

    /** Khóa theo thứ tự id cố định khi trả hàng, giảm khả năng deadlock giữa các transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from StockLot l where l.id in :ids order by l.id")
    List<StockLot> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    @Query("""
            select coalesce(sum(l.qtyAvailable), 0) from StockLot l
            where l.sku = :sku and l.qtyAvailable > 0 and l.expiresAt > :now
            """)
    long sumSellable(@Param("sku") String sku, @Param("now") Instant now);

    @Query("""
            select coalesce(sum(l.qtyAvailable), 0) from StockLot l
            where l.sku = :sku and l.qtyAvailable > 0 and l.expiresAt <= :now
            """)
    long sumExpired(@Param("sku") String sku, @Param("now") Instant now);

    Page<StockLot> findBySkuAndQtyAvailableGreaterThan(String sku, int minAvailable, Pageable pageable);

    Page<StockLot> findBySku(String sku, Pageable pageable);
}
