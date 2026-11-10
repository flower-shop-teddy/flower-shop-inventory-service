package com.flowershop.inventory.client;

import com.flowershop.common.api.ApiResponse;
import com.flowershop.common.client.RemoteServiceException;
import com.flowershop.common.client.ServiceClientFactory;
import com.flowershop.common.client.ServiceEndpoint;
import com.flowershop.common.error.BusinessException;
import com.flowershop.inventory.config.InventoryProperties;
import com.flowershop.inventory.error.InventoryErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

/** Hỏi catalog-service SKU có tồn tại và còn kinh doanh không, trước khi nhập lô. */
@Slf4j
@Component
public class CatalogClient {

    record FlowerInfo(String sku, String name, String status) {
    }

    interface FlowerApi {
        @GetExchange("/api/v1/flowers/sku/{sku}")
        ApiResponse<FlowerInfo> getBySku(@PathVariable String sku);
    }

    private final FlowerApi api;

    public CatalogClient(ServiceClientFactory clients, InventoryProperties props) {
        InventoryProperties.Catalog cfg = props.catalog();
        // API xem sản phẩm là public: không cần token
        this.api = clients.create(FlowerApi.class,
                new ServiceEndpoint("catalog-service", cfg.baseUrl(), cfg.connectTimeout(), cfg.readTimeout()), null);
    }

    /** Ném lỗi nếu SKU không tồn tại hoặc đã ngừng kinh doanh. */
    public void requireActive(String sku) {
        FlowerInfo flower;
        try {
            ApiResponse<FlowerInfo> response = api.getBySku(sku);
            flower = response == null ? null : response.data();
        } catch (RemoteServiceException e) {
            if (e.hasCode("CAT-404-001")) {
                throw new BusinessException(InventoryErrorCode.SKU_NOT_IN_CATALOG);
            }
            log.error("Call catalog-service failed: {}", e.getMessage(), e);
            throw new BusinessException(InventoryErrorCode.CATALOG_UNAVAILABLE);
        }
        if (flower == null) {
            throw new BusinessException(InventoryErrorCode.CATALOG_UNAVAILABLE);
        }
        if (!"ACTIVE".equals(flower.status())) {
            throw new BusinessException(InventoryErrorCode.SKU_INACTIVE);
        }
    }
}
