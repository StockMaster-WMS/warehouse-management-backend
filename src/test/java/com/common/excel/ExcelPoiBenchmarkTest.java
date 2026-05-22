package com.common.excel;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Locale;

class ExcelPoiBenchmarkTest {

    private static final int[] ROW_COUNTS = {500, 2_000, 10_000};
    private static final int COLUMN_COUNT = 18;

    @Test
    void benchmarkXlsxWriteAndRead() throws Exception {
        BenchmarkResult warmup = writeWorkbook(100, true);
        readWorkbook(warmup.bytes());

        System.out.println();
        System.out.println("Excel POI benchmark: rows, cols, writeNoAutoMs, writeAutoMs, readMs, autoFileKB");
        for (int rows : ROW_COUNTS) {
            BenchmarkResult noAuto = writeWorkbook(rows, false);
            BenchmarkResult auto = writeWorkbook(rows, true);
            long readMs = readWorkbook(auto.bytes());
            System.out.printf(Locale.ROOT, "%d,%d,%d,%d,%d,%d%n",
                    rows,
                    COLUMN_COUNT,
                    noAuto.elapsedMs(),
                    auto.elapsedMs(),
                    readMs,
                    auto.bytes().length / 1024);
        }
    }

    private static BenchmarkResult writeWorkbook(int rows, boolean autoSize) throws Exception {
        long start = System.nanoTime();
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Data");
            Row header = sheet.createRow(0);
            for (int c = 0; c < COLUMN_COUNT; c++) {
                header.createCell(c).setCellValue("column_" + c);
            }

            for (int r = 1; r <= rows; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < COLUMN_COUNT; c++) {
                    if (c % 5 == 0) {
                        row.createCell(c).setCellValue(r * (c + 1));
                    } else {
                        row.createCell(c).setCellValue("value-" + r + "-" + c);
                    }
                }
            }

            if (autoSize) {
                for (int c = 0; c < COLUMN_COUNT; c++) {
                    sheet.autoSizeColumn(c);
                }
            }

            workbook.write(out);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            return new BenchmarkResult(elapsedMs, out.toByteArray());
        }
    }

    private static long readWorkbook(byte[] bytes) throws Exception {
        long start = System.nanoTime();
        DataFormatter formatter = new DataFormatter();
        int nonBlankCells = 0;
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                for (int c = 0; c < COLUMN_COUNT; c++) {
                    String value = formatter.formatCellValue(row.getCell(c));
                    if (value != null && !value.isBlank()) {
                        nonBlankCells++;
                    }
                }
            }
        }
        if (nonBlankCells == 0) {
            throw new AssertionError("Benchmark read did not parse any cells");
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    private record BenchmarkResult(long elapsedMs, byte[] bytes) {
    }
}
