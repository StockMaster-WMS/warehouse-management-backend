package com.common.excel;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

/**
 * Đọc giá trị ô theo map cột đã parse từ header.
 */
public final class ExcelRowReader {

    private ExcelRowReader() {}

    public static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s;
    }

    public static String requireString(Row row, Map<String, Integer> col, String key, DataFormatter fmt) {
        Integer idx = col.get(key);
        if (idx == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Thiếu cột: " + key);
        }
        String s = cellString(row.getCell(idx), fmt);
        if (s == null || s.isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cột " + key + " không được để trống");
        }
        return s;
    }

    public static String optionalString(Row row, Map<String, Integer> col, String key, DataFormatter fmt) {
        Integer idx = col.get(key);
        if (idx == null) {
            return null;
        }
        return cellString(row.getCell(idx), fmt);
    }

    public static String cellString(Cell cell, DataFormatter fmt) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        String s = fmt.formatCellValue(cell);
        return s == null ? null : s.trim();
    }

    public static BigDecimal optionalBigDecimal(Row row, Map<String, Integer> col, String key,
            DataFormatter fmt) {
        Integer idx = col.get(key);
        if (idx == null) {
            return null;
        }
        Cell cell = row.getCell(idx);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String s = fmt.formatCellValue(cell).trim().replace(",", ".");
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cột " + key + " phải là số");
        }
    }

    public static Integer optionalInteger(Row row, Map<String, Integer> col, String key, DataFormatter fmt) {
        Integer idx = col.get(key);
        if (idx == null) {
            return null;
        }
        Cell cell = row.getCell(idx);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double v = cell.getNumericCellValue();
            if (v != Math.rint(v)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Cột " + key + " phải là số nguyên");
            }
            return (int) v;
        }
        String s = fmt.formatCellValue(cell).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s.replace(",", ""));
        } catch (NumberFormatException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cột " + key + " phải là số nguyên");
        }
    }

    public static Boolean optionalBoolean(Row row, Map<String, Integer> col, String key, DataFormatter fmt) {
        Integer idx = col.get(key);
        if (idx == null) {
            return null;
        }
        String s = cellString(row.getCell(idx), fmt);
        if (s == null || s.isBlank()) {
            return null;
        }
        return parseBooleanCell(s);
    }

    public static boolean parseBooleanCell(String s) {
        String v = s.trim().toLowerCase(Locale.ROOT);
        if (v.equals("true") || v.equals("1") || v.equals("yes") || v.equals("y")
                || v.equals("có") || v.equals("co")) {
            return true;
        }
        if (v.equals("false") || v.equals("0") || v.equals("no") || v.equals("n")
                || v.equals("không") || v.equals("khong")) {
            return false;
        }
        throw new AppException(ErrorCode.BAD_REQUEST, "Giá trị true/false không hợp lệ: " + s);
    }
}
