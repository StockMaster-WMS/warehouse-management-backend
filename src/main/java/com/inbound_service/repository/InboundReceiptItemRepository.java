package com.inbound_service.repository;

import com.inbound_service.entity.InboundReceiptItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InboundReceiptItemRepository extends JpaRepository<InboundReceiptItem, UUID> {

    List<InboundReceiptItem> findByReceiptId(UUID receiptId);
}
