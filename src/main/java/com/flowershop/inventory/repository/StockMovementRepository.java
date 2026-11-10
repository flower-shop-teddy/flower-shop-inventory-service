package com.flowershop.inventory.repository;

import com.flowershop.inventory.domain.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByLotIdOrderByIdAsc(Long lotId);

    @Query("select coalesce(sum(m.quantityDelta), 0) from StockMovement m where m.lotId = :lotId")
    long sumDelta(@Param("lotId") Long lotId);
}
