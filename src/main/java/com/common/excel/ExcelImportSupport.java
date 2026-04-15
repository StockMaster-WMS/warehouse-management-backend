package com.common.excel;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Tiện ích import .xlsx cấp sheet: chuẩn hóa tiêu đề, parse header, kiểm tra cột, dòng trống, đuôi file.
 * Đọc giá trị từng ô dùng {@link ExcelRowReader}.
 */
public final class ExcelImportSupport {

    private ExcelImportSupport() {}

    /** Chuẩn hóa tiêu đề cột để map alias (đa ngôn ngữ / nhiều cách viết). */
    public static String normalizeForAlias(String raw) {
        return stripAccents(raw.trim().toLowerCase(Locale.ROOT)).replace("_", "").replace(" ", "");
    }

    public static String stripAccents(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}+", "");
    }

    /**
     * Dựng map tên cột chuẩn → chỉ số cột từ dòng tiêu đề.
     *
     * @param resolveCanonicalKey text ô tiêu đề → key nội bộ (vd. {@code name}) nếu nhận diện được
     */
    public static Map<String, Integer> parseHeaderRow(
            Row headerRow,
            DataFormatter fmt,
            Function<String, Optional<String>> resolveCanonicalKey) {
        Map<String, Integer> map = new HashMap<>();
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            final int colIndex = c;
            Cell cell = headerRow.getCell(colIndex);
            if (cell == null) {
                continue;
            }
            String raw = fmt.formatCellValue(cell);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            resolveCanonicalKey.apply(raw).ifPresent(key -> map.put(key, colIndex));
        }
        return map;
    }

    /**
     * @param detailHint gợi ý cho user khi thiếu cột
     */
    public static void requireColumns(
            Map<String, Integer> columnIndexByKey,
            Collection<String> requiredCanonicalKeys,
            String detailHint) {
        List<String> missing = new ArrayList<>();
        for (String key : requiredCanonicalKeys) {
            if (!columnIndexByKey.containsKey(key)) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Thiếu cột bắt buộc: " + String.join(", ", missing) + ". " + detailHint);
        }
    }

    /**
     * @param primaryColumnKey cột chính để coi dòng là trống (vd. {@code name}); không có trong map thì quét mọi ô
     */
    public static boolean isRowEffectivelyEmpty(
            Row row,
            Map<String, Integer> col,
            DataFormatter fmt,
            String primaryColumnKey) {
        if (primaryColumnKey != null && col.containsKey(primaryColumnKey)) {
            String n = ExcelRowReader.cellString(row.getCell(col.get(primaryColumnKey)), fmt);
            return n == null || n.isBlank();
        }
        short last = row.getLastCellNum();
        if (last <= 0) {
            return true;
        }
        for (int c = 0; c < last; c++) {
            String v = ExcelRowReader.cellString(row.getCell(c), fmt);
            if (v != null && !v.isBlank()) {
                return false;
            }
        }
        return true;
    }

    public static void requireNonEmptyMultipartName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tên file không hợp lệ");
        }
    }

    public static void requireXlsxExtension(String originalFilename) {
        String name = originalFilename != null ? originalFilename : "";
        if (!name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ hỗ trợ file .xlsx");
        }
    }
}
