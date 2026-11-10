package com.flowershop.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** Sổ cái kho, chỉ thêm. @Immutable: Hibernate không bao giờ sinh câu UPDATE cho entity này. */
@Entity
@Immutable
@Table(name = "stock_movement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lot_id", nullable = false)
    private Long lotId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private MovementType movementType;

    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta;

    @Column(length = 100)
    private String reference;

    @Column(length = 100)
    private String actor;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public static StockMovement of(Long lotId, MovementType type, int quantityDelta, String reference,
                                   String actor, String correlationId, Instant now) {
        StockMovement m = new StockMovement();
        m.lotId = lotId;
        m.movementType = type;
        m.quantityDelta = quantityDelta;
        m.reference = reference;
        m.actor = actor;
        m.correlationId = correlationId;
        m.occurredAt = now;
        return m;
    }
}
