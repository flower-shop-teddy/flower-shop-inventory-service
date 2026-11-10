package com.flowershop.inventory.repository;

import com.flowershop.inventory.domain.StockReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    @EntityGraph(attributePaths = {"lines", "lines.lot"})
    Optional<StockReservation> findByOrderCode(String orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StockReservation r where r.orderCode = :orderCode")
    Optional<StockReservation> findByOrderCodeForUpdate(@Param("orderCode") String orderCode);

    /**
     * Phiếu HELD đã quá hạn giữ. SKIP LOCKED: chạy nhiều instance cùng lúc thì mỗi instance lấy một phần,
     * không instance nào phải đợi, và không phiếu nào bị nhả hai lần.
     */
    @Query(value = """
            SELECT * FROM stock_reservation
            WHERE status = 'HELD' AND hold_until < :now
            ORDER BY hold_until
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<StockReservation> findOverdueForUpdate(@Param("now") Instant now, @Param("limit") int limit);
}
