package com.flowershop.inventory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Mỗi phút nhả các phiếu giữ hàng quá hạn (khách bỏ dở, order-service lỗi giữa chừng...).
 * Chạy nhiều instance cùng lúc vẫn an toàn nhờ FOR UPDATE SKIP LOCKED.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "inventory.reservation.expiry-job-enabled", havingValue = "true", matchIfMissing = true)
public class ReservationExpiryJob {

    private final ReservationService reservations;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${inventory.reservation.expiry-interval-ms:60000}",
            initialDelayString = "${inventory.reservation.expiry-interval-ms:60000}")
    public void run() {
        try {
            reservations.expireOverdue(clock.instant());
        } catch (RuntimeException e) {
            // Không để lỗi một lần làm dừng hẳn lịch chạy; lần sau thử lại
            log.error("Reservation expiry job failed", e);
        }
    }
}
