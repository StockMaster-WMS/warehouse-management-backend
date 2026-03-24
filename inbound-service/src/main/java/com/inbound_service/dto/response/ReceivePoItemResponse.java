package com.inbound_service.dto.response;

public record ReceivePoItemResponse(
        PoItemResponse poItem,
        PutawayTaskResponse putawayTask
) {
}
