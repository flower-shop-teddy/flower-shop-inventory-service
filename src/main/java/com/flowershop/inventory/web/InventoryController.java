package com.flowershop.inventory.web;

import com.flowershop.common.api.ApiResponse;
import com.flowershop.common.api.PageResponse;
import com.flowershop.inventory.dto.AvailabilityResponse;
import com.flowershop.inventory.dto.LotResponse;
import com.flowershop.inventory.dto.MovementResponse;
import com.flowershop.inventory.dto.ReceiveLotRequest;
import com.flowershop.inventory.dto.StockResponse;
import com.flowershop.inventory.dto.WasteRequest;
import com.flowershop.inventory.service.LotService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final LotService lotService;

    @PostMapping("/lots")
    @PreAuthorize("hasAuthority('inventory:write')")
    public ResponseEntity<ApiResponse<LotResponse>> receive(@Valid @RequestBody ReceiveLotRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(lotService.receive(req)));
    }

    @PostMapping("/lots/{lotCode}/waste")
    @PreAuthorize("hasAuthority('inventory:write')")
    public ApiResponse<LotResponse> waste(@PathVariable String lotCode, @Valid @RequestBody WasteRequest req) {
        return ApiResponse.success(lotService.waste(lotCode, req));
    }

    @GetMapping("/lots")
    @PreAuthorize("hasAuthority('inventory:read')")
    public ApiResponse<PageResponse<LotResponse>> listLots(@RequestParam String sku,
                                                           @RequestParam(defaultValue = "false") boolean includeEmpty,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(lotService.listLots(sku, includeEmpty, page, size));
    }

    @GetMapping("/lots/{lotCode}")
    @PreAuthorize("hasAuthority('inventory:read')")
    public ApiResponse<LotResponse> getLot(@PathVariable String lotCode) {
        return ApiResponse.success(lotService.getLot(lotCode));
    }

    @GetMapping("/lots/{lotCode}/movements")
    @PreAuthorize("hasAuthority('inventory:read')")
    public ApiResponse<List<MovementResponse>> movements(@PathVariable String lotCode) {
        return ApiResponse.success(lotService.movements(lotCode));
    }

    @GetMapping("/stock")
    @PreAuthorize("hasAuthority('inventory:read')")
    public ApiResponse<StockResponse> stock(@RequestParam String sku) {
        return ApiResponse.success(lotService.stock(sku));
    }

    /** Public: khách xem sản phẩm còn hàng không. */
    @GetMapping("/availability")
    public ApiResponse<AvailabilityResponse> availability(@RequestParam String sku) {
        return ApiResponse.success(lotService.availability(sku));
    }
}
