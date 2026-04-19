package com.inbound_service.mapper;

import com.inbound_service.dto.response.InboundReceiptItemResponse;
import com.inbound_service.dto.response.InboundReceiptResponse;
import com.inbound_service.entity.InboundReceipt;
import com.inbound_service.entity.InboundReceiptItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "default")
public interface InboundReceiptMapper {

    @Mapping(target = "purchaseOrderId", source = "purchaseOrder.id")
    @Mapping(target = "poNumber", source = "purchaseOrder.poNumber")
    InboundReceiptResponse toResponse(InboundReceipt receipt);

    @Mapping(target = "poItemId", source = "poItem.id")
    InboundReceiptItemResponse toItemResponse(InboundReceiptItem item);

    List<InboundReceiptItemResponse> toItemResponseList(List<InboundReceiptItem> items);
}
