package com.flowershop.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Giữ {@code quantity} của SKU từ một lô cụ thể. */
@Entity
@Table(name = "stock_reservation_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReservationLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, updatable = false)
    private StockReservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false, updatable = false)
    private StockLot lot;

    @Column(nullable = false, length = 30, updatable = false)
    private String sku;

    @Column(nullable = false, updatable = false)
    private int quantity;

    static StockReservationLine of(StockReservation reservation, StockLot lot, int quantity) {
        StockReservationLine line = new StockReservationLine();
        line.reservation = reservation;
        line.lot = lot;
        line.sku = lot.getSku();
        line.quantity = quantity;
        return line;
    }
}
