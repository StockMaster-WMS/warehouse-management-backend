package com.warehouse_service.config;

import com.warehouse_service.mapper.LocationMapper;
import com.warehouse_service.mapper.StockLevelMapper;
import com.warehouse_service.mapper.WarehouseMapper;
import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Đăng ký MapStruct mapper bằng {@link Mappers#getMapper(Class)} thay vì {@code componentModel = "spring"},
 * tránh lỗi thiếu bean khi chạy kèm Spring DevTools / classpath do IDE tạo.
 */
@Configuration
public class MapStructMapperConfig {

    @Bean
    public LocationMapper locationMapper() {
        return Mappers.getMapper(LocationMapper.class);
    }

    @Bean
    public WarehouseMapper warehouseMapper() {
        return Mappers.getMapper(WarehouseMapper.class);
    }

    @Bean
    public StockLevelMapper stockLevelMapper() {
        return Mappers.getMapper(StockLevelMapper.class);
    }
}
