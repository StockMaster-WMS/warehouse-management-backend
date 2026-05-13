package com.common.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RevenueTrendResponse(
    LocalDate date,
    BigDecimal revenue
) {}
