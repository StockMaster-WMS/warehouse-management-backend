package com.inbound_service.config;

import com.inbound_service.mapper.InboundReceiptMapper;
import com.inbound_service.mapper.PoItemMapper;
import com.inbound_service.mapper.PurchaseOrderMapper;
import com.inbound_service.mapper.PutawayTaskMapper;
import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MapStructMapperConfig {

    @Bean
    public PoItemMapper poItemMapper() {
        return Mappers.getMapper(PoItemMapper.class);
    }

    @Bean
    public PurchaseOrderMapper purchaseOrderMapper() {
        return Mappers.getMapper(PurchaseOrderMapper.class);
    }

    @Bean
    public PutawayTaskMapper putawayTaskMapper() {
        return Mappers.getMapper(PutawayTaskMapper.class);
    }

    @Bean
    public InboundReceiptMapper inboundReceiptMapper() {
        return Mappers.getMapper(InboundReceiptMapper.class);
    }
}
