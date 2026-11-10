package com.flowershop.inventory.web;

import com.flowershop.common.api.ApiResponse;
import com.flowershop.inventory.dto.ReservationResponse;
import com.flowershop.inventory.dto.ReserveRequest;
import com.flowershop.inventory.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API nội bộ cho order-service. /internal/** không có route ở gateway (và gateway denyAll),
 * thêm vào đó cần permission inventory:reserve mà chỉ service client mới có.
 */
@RestController
@RequestMapping("/internal/v1/reservations")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('inventory:reserve')")
public class ReservationController {

    private final ReservationService reservationService;

    /** 201: vừa giữ hàng. 200: gọi lại cùng orderCode, trả phiếu đã có (không giữ thêm). */
    @PostMapping
    public ResponseEntity<ApiResponse<ReservationResponse>> reserve(@Valid @RequestBody ReserveRequest req) {
        ReservationService.ReserveResult result = reservationService.reserve(req);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.success(result.reservation()));
    }

    @GetMapping("/{orderCode}")
    public ApiResponse<ReservationResponse> get(@PathVariable String orderCode) {
        return ApiResponse.success(reservationService.get(orderCode));
    }

    @PostMapping("/{orderCode}/confirm")
    public ApiResponse<ReservationResponse> confirm(@PathVariable String orderCode) {
        return ApiResponse.success(reservationService.confirm(orderCode));
    }

    @PostMapping("/{orderCode}/release")
    public ApiResponse<ReservationResponse> release(@PathVariable String orderCode) {
        return ApiResponse.success(reservationService.release(orderCode));
    }
}
