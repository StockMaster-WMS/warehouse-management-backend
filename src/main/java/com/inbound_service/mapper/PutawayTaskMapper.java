package com.inbound_service.mapper;

import com.inbound_service.dto.response.PutawayTaskResponse;
import com.inbound_service.entity.PutawayTask;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "default")
public interface PutawayTaskMapper {

    @Mapping(target = "poItemId", source = "poItem.id")
    @Mapping(target = "inboundReceiptId", source = "inboundReceipt.id")
    PutawayTaskResponse toResponse(PutawayTask task);
}
