package com.minhnbnt.fashionstore.features.shop.excel;

import com.minhnbnt.fashionstore.features.shop.ShopStatsRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SellerExcelReportBuilderTest {

    private final LocalDate fromDate = LocalDate.of(2026, 10, 1);
    private final LocalDate toDate = LocalDate.of(2026, 10, 25);

    @Test
    @DisplayName("Tạo file Excel (.xlsx) đa sheet thành công với đủ 4 sheet chuẩn")
    void buildsWorkbookWithFourSheetsAndCorrectContent() throws IOException {
        var summary = new ShopStatsRepository.OrderStatusSummaryRow(
            86L, 52_920_000L, 10L, 7_440_000L, 2L, 700_000L
        );

        var periodRows = List.of(
            new ShopStatsRepository.RevenuePeriodRow("2026-10-21", 14_200_000L, 18L),
            new ShopStatsRepository.RevenuePeriodRow("2026-10-22", 10_500_000L, 12L)
        );

        var paymentRows = List.of(
            new ShopStatsRepository.RevenueByPaymentRow("COD", 89_060_000L, 150L)
        );

        var productRows = List.of(
            new ShopStatsRepository.ProductPerformanceRow(
                UUID.randomUUID(), "Áo khoác gió Unisex", "Áo khoác", "AK-01", 450_000L, 94L, 42_300_000L, 45
            )
        );

        var orderRows = List.of(
            new ShopStatsRepository.OrderExportRow(
                UUID.randomUUID(), "ORD-20261021-001", Instant.now(), "Nguyễn Văn A", "0987654321",
                "DELIVERED", "COD", 450_000L, 30_000L, 480_000L
            )
        );

        var builder = new SellerExcelReportBuilder();
        var bytes = builder.buildExcelReport(
            "Nét Boutique Test",
            "net-boutique-test",
            fromDate,
            toDate,
            summary,
            periodRows,
            paymentRows,
            productRows,
            orderRows,
            true
        );

        assertThat(bytes).isNotEmpty();

        // Đọc lại workbook từ bytes để xác thực cấu trúc
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(4);
            assertThat(workbook.getSheetName(0)).isEqualTo("Tổng quan kinh doanh");
            assertThat(workbook.getSheetName(1)).isEqualTo("Doanh thu theo chu kỳ");
            assertThat(workbook.getSheetName(2)).isEqualTo("Sản phẩm bán chạy");
            assertThat(workbook.getSheetName(3)).isEqualTo("Sổ chi tiết đơn hàng");

            // Sheet 1: Kiểm tra metadata shop & KPIs
            var sheet1 = workbook.getSheetAt(0);
            assertThat(sheet1.getRow(1).getCell(1).getStringCellValue()).contains("Nét Boutique Test");
            
            // Sheet 2: Kiểm tra dòng dữ liệu và dòng tổng cộng
            var sheet2 = workbook.getSheetAt(1);
            assertThat(sheet2.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Ngày");
            assertThat(sheet2.getRow(1).getCell(0).getStringCellValue()).isEqualTo("2026-10-21");

            // Sheet 3: Kiểm tra sản phẩm
            var sheet3 = workbook.getSheetAt(2);
            assertThat(sheet3.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Áo khoác gió Unisex");
            assertThat(sheet3.getRow(1).getCell(2).getStringCellValue()).isEqualTo("Áo khoác");

            // Sheet 4: Kiểm tra che số điện thoại bảo mật (Masked Phone)
            var sheet4 = workbook.getSheetAt(3);
            var phoneCell = sheet4.getRow(1).getCell(3).getStringCellValue();
            assertThat(phoneCell).isEqualTo("098****321");
        }
    }

    @Test
    @DisplayName("Tạo file CSV UTF-8 BOM chuẩn hiển thị tiếng Việt không bị lỗi font")
    void buildsCsvReportWithUtf8BomAndVietnameseHeaders() {
        var periodRows = List.of(
            new ShopStatsRepository.RevenuePeriodRow("2026-10-21", 14_200_000L, 18L)
        );

        var builder = new SellerExcelReportBuilder();
        var bytes = builder.buildCsvReport(periodRows);

        assertThat(bytes).isNotEmpty();
        // Kiểm tra 3 byte đầu tiên là UTF-8 BOM: 0xEF, 0xBB, 0xBF
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);

        var csvString = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csvString).startsWith("\uFEFFNgày,Doanh thu (VND),Số đơn hàng");
        assertThat(csvString).contains("2026-10-21,14200000,18");
    }
}
