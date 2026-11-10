package com.flowershop.inventory.service;

import com.flowershop.common.security.AuthenticatedUser;
import com.flowershop.common.security.CurrentUser;
import com.flowershop.common.web.CorrelationId;
import com.flowershop.inventory.domain.MovementType;
import com.flowershop.inventory.domain.StockLot;
import com.flowershop.inventory.domain.StockMovement;
import com.flowershop.inventory.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Ghi sổ cái kho, TRONG CÙNG transaction với thay đổi số lượng của lô:
 * số lượng đổi thì sổ cái có dòng tương ứng, rollback thì cả hai cùng mất.
 */
@Component
@RequiredArgsConstructor
public class MovementRecorder {

    static final String SYSTEM_ACTOR = "system";

    private final StockMovementRepository movements;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(StockLot lot, MovementType type, int quantityDelta, String reference) {
        String actor = CurrentUser.get().map(AuthenticatedUser::subject).orElse(SYSTEM_ACTOR);
        movements.save(StockMovement.of(lot.getId(), type, quantityDelta, reference, actor,
                MDC.get(CorrelationId.MDC_KEY), clock.instant()));
    }
}
