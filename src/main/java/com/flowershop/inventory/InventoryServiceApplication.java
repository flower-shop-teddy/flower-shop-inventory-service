package com.flowershop.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// Quét thêm package common để nhận GlobalExceptionHandler, CorrelationIdFilter, SecurityExceptionHandler từ common-lib
@SpringBootApplication(scanBasePackages = {"com.flowershop.inventory", "com.flowershop.common"})
@ConfigurationPropertiesScan
@EnableScheduling
public class InventoryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
