package com.inbound_service.repository;

import com.inbound_service.entity.InboundReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InboundReceiptRepository extends JpaRepository<InboundReceipt, UUID>,
        JpaSpecificationExecutor<InboundReceipt> {

    List<InboundReceipt> findByPurchaseOrderId(UUID purchaseOrderId);

    Optional<InboundReceipt> findByReceiptNumber(String receiptNumber);

    boolean existsByReceiptNumber(String receiptNumber);
}
