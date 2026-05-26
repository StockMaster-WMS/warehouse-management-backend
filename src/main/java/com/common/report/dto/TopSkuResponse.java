package com.common.report.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TopSkuResponse(
    UUID productId,
    String productSku,
    String productName,
    Long totalQty,
    BigDecimal totalRevenue
) {}
