package com.common.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenueTrendResponse(
    LocalDate date,
    BigDecimal revenue
) {}
