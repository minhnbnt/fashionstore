package com.minhnbnt.fashionstore.features.shop.excel;

import com.minhnbnt.fashionstore.features.shop.ShopStatsRepository;
import com.minhnbnt.fashionstore.model.Shop;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class SellerExcelReportBuilder {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final byte[] COLOR_INDIGO_HEADER = new byte[]{(byte) 79, (byte) 70, (byte) 229}; // #4F46E5
    private static final byte[] COLOR_LIGHT_INDIGO = new byte[]{(byte) 238, (byte) 242, (byte) 255}; // #EEF2FF
    private static final byte[] COLOR_BORDER_GRAY = new byte[]{(byte) 209, (byte) 213, (byte) 219}; // #D1D5DB

    public byte[] buildExcelReport(
        String shopName,
        String shopSlug,
        LocalDate fromDate,
        LocalDate toDate,
        ShopStatsRepository.OrderStatusSummaryRow summary,
        List<ShopStatsRepository.RevenuePeriodRow> periodRows,
        List<ShopStatsRepository.RevenueByPaymentRow> paymentRows,
        List<ShopStatsRepository.ProductPerformanceRow> productRows,
        List<ShopStatsRepository.OrderExportRow> orderRows,
        boolean maskPhone
    ) {
        try (var workbook = new XSSFWorkbook()) {
            var styles = createReportStyles(workbook);

            // Sheet 1: Tổng quan kinh doanh
            buildSummarySheet(workbook, styles, shopName, shopSlug, fromDate, toDate, summary, paymentRows);

            // Sheet 2: Doanh thu theo chu kỳ
            buildPeriodSheet(workbook, styles, periodRows);

            // Sheet 3: Sản phẩm bán chạy
            buildProductsSheet(workbook, styles, productRows);

            // Sheet 4: Sổ chi tiết đơn hàng
            buildOrdersSheet(workbook, styles, orderRows, maskPhone);

            try (var out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Lỗi khi kết xuất file báo cáo Excel: " + e.getMessage(), e);
        }
    }

    public byte[] buildCsvReport(List<ShopStatsRepository.RevenuePeriodRow> periodRows) {
        var sb = new StringBuilder();
        // UTF-8 BOM byte marker
        sb.append('\ufeff');
        sb.append("Ngày,Doanh thu (VND),Số đơn hàng\n");

        for (var row : periodRows) {
            sb.append(row.periodLabel()).append(',')
              .append(row.revenue()).append(',')
              .append(row.orders()).append('\n');
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ==========================================
    // SHEET 1: TỔNG QUAN KINH DOANH
    // ==========================================
    private void buildSummarySheet(
        XSSFWorkbook workbook,
        ReportStyles styles,
        String shopName,
        String shopSlug,
        LocalDate fromDate,
        LocalDate toDate,
        ShopStatsRepository.OrderStatusSummaryRow summary,
        List<ShopStatsRepository.RevenueByPaymentRow> paymentRows
    ) {
        var sheet = workbook.createSheet("Tổng quan kinh doanh");
        sheet.setDisplayGridlines(true);

        // Header Title
        var titleRow = sheet.createRow(0);
        var titleCell = titleRow.createCell(0);
        titleCell.setCellValue("BÁO CÁO TỔNG QUAN HOẠT ĐỘNG KINH DOANH GIAN HÀNG");
        titleCell.setCellStyle(styles.sheetTitleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

        // Shop Metadata
        var metaRow1 = sheet.createRow(1);
        metaRow1.createCell(0).setCellValue("Tên gian hàng:");
        metaRow1.getCell(0).setCellStyle(styles.metaLabelStyle);
        metaRow1.createCell(1).setCellValue(shopName + " (" + shopSlug + ")");
        metaRow1.getCell(1).setCellStyle(styles.metaValueStyle);

        var metaRow2 = sheet.createRow(2);
        metaRow2.createCell(0).setCellValue("Kỳ báo cáo:");
        metaRow2.getCell(0).setCellStyle(styles.metaLabelStyle);
        metaRow2.createCell(1).setCellValue("Từ " + fromDate.format(DATE_FORMATTER) + " đến " + toDate.format(DATE_FORMATTER));
        metaRow2.getCell(1).setCellStyle(styles.metaValueStyle);

        var metaRow3 = sheet.createRow(3);
        metaRow3.createCell(0).setCellValue("Ngày kết xuất:");
        metaRow3.getCell(0).setCellStyle(styles.metaLabelStyle);
        metaRow3.createCell(1).setCellValue(LocalDate.now(VN_ZONE).format(DATE_FORMATTER));
        metaRow3.getCell(1).setCellStyle(styles.metaValueStyle);

        // Section 1: Core KPIs
        var kpiHeaderRow = sheet.createRow(5);
        var kpiHeaderCell = kpiHeaderRow.createCell(0);
        kpiHeaderCell.setCellValue("1. CÁC CHỈ SỐ KINH DOANH CỐT LÕI (CORE KPIS)");
        kpiHeaderCell.setCellStyle(styles.sectionTitleStyle);
        sheet.addMergedRegion(new CellRangeAddress(5, 5, 0, 3));

        var tblHeader = sheet.createRow(6);
        createCell(tblHeader, 0, "Chỉ số tài chính", styles.headerStyle);
        createCell(tblHeader, 1, "Giá trị", styles.headerStyle);
        createCell(tblHeader, 2, "Đơn vị", styles.headerStyle);
        createCell(tblHeader, 3, "Diễn giải nghiệp vụ", styles.headerStyle);

        int r = 7;
        r = addKpiRow(sheet, r, "Tổng doanh thu bán hàng", summary.completedValue(), styles.currencyStyle, "VND", "Doanh thu các đơn giao thành công (DELIVERED)", styles);
        r = addKpiRow(sheet, r, "Tổng số đơn hoàn thành", summary.completedOrders(), styles.integerStyle, "Đơn", "Số đơn giao thành công tới người mua", styles);

        long aov = summary.completedOrders() > 0 ? summary.completedValue() / summary.completedOrders() : 0;
        r = addKpiRow(sheet, r, "Giá trị trung bình / đơn (AOV)", aov, styles.currencyStyle, "VND", "Tổng doanh thu / Số đơn thành công", styles);
        r = addKpiRow(sheet, r, "Tổng giá trị đơn đã hủy", summary.cancelledValue(), styles.currencyStyle, "VND", "Đơn hàng bị người mua hoặc shop hủy (CANCELLED)", styles);
        r = addKpiRow(sheet, r, "Số lượng đơn đã hủy", summary.cancelledOrders(), styles.integerStyle, "Đơn", "Số đơn hủy trong kỳ", styles);
        r = addKpiRow(sheet, r, "Tổng tiền hoàn đổi trả", summary.returnedValue(), styles.currencyStyle, "VND", "Yêu cầu đổi trả đã chấp thuận hoàn tiền", styles);
        r = addKpiRow(sheet, r, "Số đơn đổi trả hoàn tiền", summary.returnedOrders(), styles.integerStyle, "Đơn", "Số đơn đổi trả thành công", styles);

        long platformFee = Math.round(summary.completedValue() * 0.05);
        r = addKpiRow(sheet, r, "Ước tính phí nền tảng (5%)", platformFee, styles.currencyStyle, "VND", "Phí dịch vụ sàn thương mại nét", styles);

        long profit = summary.completedValue() - platformFee;
        r = addKpiRow(sheet, r, "Lợi nhuận kinh doanh tạm tính", profit, styles.currencyStyle, "VND", "Doanh thu sau khi trừ phí nền tảng", styles);

        // Section 2: Payment Breakdown
        r += 2;
        var payHeaderRow = sheet.createRow(r++);
        var payHeaderCell = payHeaderRow.createCell(0);
        payHeaderCell.setCellValue("2. CƠ CẤU THEO HÌNH THỨC THANH TOÁN");
        payHeaderCell.setCellStyle(styles.sectionTitleStyle);
        sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, 3));

        var payTblHeader = sheet.createRow(r++);
        createCell(payTblHeader, 0, "Phương thức thanh toán", styles.headerStyle);
        createCell(payTblHeader, 1, "Số đơn hàng", styles.headerStyle);
        createCell(payTblHeader, 2, "Doanh thu (VND)", styles.headerStyle);
        createCell(payTblHeader, 3, "Tỷ trọng doanh thu", styles.headerStyle);

        long totalPayRevenue = paymentRows.stream().mapToLong(ShopStatsRepository.RevenueByPaymentRow::revenue).sum();
        for (var p : paymentRows) {
            var row = sheet.createRow(r++);
            createCell(row, 0, p.paymentMethod(), styles.dataStyle);
            createNumericCell(row, 1, p.orderCount(), styles.integerStyle);
            createNumericCell(row, 2, p.revenue(), styles.currencyStyle);
            double ratio = totalPayRevenue > 0 ? (double) p.revenue() / totalPayRevenue : 0.0;
            createNumericCell(row, 3, ratio, styles.percentStyle);
        }

        autoSizeColumns(sheet, 4);
    }

    private int addKpiRow(
        XSSFSheet sheet,
        int rowIndex,
        String label,
        long value,
        CellStyle valueStyle,
        String unit,
        String note,
        ReportStyles styles
    ) {
        var row = sheet.createRow(rowIndex);
        createCell(row, 0, label, styles.dataStyle);
        createNumericCell(row, 1, value, valueStyle);
        createCell(row, 2, unit, styles.dataCenteredStyle);
        createCell(row, 3, note, styles.dataStyle);
        return rowIndex + 1;
    }

    // ==========================================
    // SHEET 2: DOANH THU THEO CHU KỲ
    // ==========================================
    private void buildPeriodSheet(
        XSSFWorkbook workbook,
        ReportStyles styles,
        List<ShopStatsRepository.RevenuePeriodRow> periodRows
    ) {
        var sheet = workbook.createSheet("Doanh thu theo chu kỳ");
        sheet.createFreezePane(0, 1);
        sheet.setDisplayGridlines(true);

        var headerRow = sheet.createRow(0);
        createCell(headerRow, 0, "Ngày", styles.headerStyle);
        createCell(headerRow, 1, "Số đơn hoàn thành", styles.headerStyle);
        createCell(headerRow, 2, "Doanh thu thực nhận (VND)", styles.headerStyle);

        int r = 1;
        for (var p : periodRows) {
            var row = sheet.createRow(r++);
            createCell(row, 0, p.periodLabel(), styles.dataCenteredStyle);
            createNumericCell(row, 1, p.orders(), styles.integerStyle);
            createNumericCell(row, 2, p.revenue(), styles.currencyStyle);
        }

        // Dòng tổng cộng
        if (!periodRows.isEmpty()) {
            var totalRow = sheet.createRow(r);
            createCell(totalRow, 0, "TỔNG CỘNG", styles.totalLabelStyle);
            
            var ordersFormula = totalRow.createCell(1);
            ordersFormula.setCellFormula("SUM(B2:B" + r + ")");
            ordersFormula.setCellStyle(styles.totalIntegerStyle);

            var revenueFormula = totalRow.createCell(2);
            revenueFormula.setCellFormula("SUM(C2:C" + r + ")");
            revenueFormula.setCellStyle(styles.totalCurrencyStyle);
        }

        autoSizeColumns(sheet, 3);
    }

    // ==========================================
    // SHEET 3: HIỆU SUẤT SẢN PHẨM & DANH MỤC
    // ==========================================
    private void buildProductsSheet(
        XSSFWorkbook workbook,
        ReportStyles styles,
        List<ShopStatsRepository.ProductPerformanceRow> productRows
    ) {
        var sheet = workbook.createSheet("Sản phẩm bán chạy");
        sheet.createFreezePane(0, 1);
        sheet.setDisplayGridlines(true);

        var headerRow = sheet.createRow(0);
        createCell(headerRow, 0, "STT", styles.headerStyle);
        createCell(headerRow, 1, "Tên sản phẩm", styles.headerStyle);
        createCell(headerRow, 2, "Danh mục", styles.headerStyle);
        createCell(headerRow, 3, "Mã SKU", styles.headerStyle);
        createCell(headerRow, 4, "Đơn giá TB (VND)", styles.headerStyle);
        createCell(headerRow, 5, "Số lượng đã bán", styles.headerStyle);
        createCell(headerRow, 6, "Doanh thu (VND)", styles.headerStyle);
        createCell(headerRow, 7, "Tồn kho hiện tại", styles.headerStyle);

        int r = 1;
        int index = 1;
        for (var p : productRows) {
            var row = sheet.createRow(r++);
            createNumericCell(row, 0, index++, styles.dataCenteredStyle);
            createCell(row, 1, p.productName(), styles.dataStyle);
            createCell(row, 2, p.categoryName(), styles.dataStyle);
            createCell(row, 3, p.sku(), styles.dataCenteredStyle);
            createNumericCell(row, 4, p.price(), styles.currencyStyle);
            createNumericCell(row, 5, p.totalSold(), styles.integerStyle);
            createNumericCell(row, 6, p.totalRevenue(), styles.currencyStyle);
            createNumericCell(row, 7, p.currentStock(), styles.integerStyle);
        }

        autoSizeColumns(sheet, 8);
    }

    // ==========================================
    // SHEET 4: SỔ CHI TIẾT ĐƠN HÀNG
    // ==========================================
    private void buildOrdersSheet(
        XSSFWorkbook workbook,
        ReportStyles styles,
        List<ShopStatsRepository.OrderExportRow> orderRows,
        boolean maskPhone
    ) {
        var sheet = workbook.createSheet("Sổ chi tiết đơn hàng");
        sheet.createFreezePane(0, 1);
        sheet.setDisplayGridlines(true);

        var headerRow = sheet.createRow(0);
        createCell(headerRow, 0, "Mã đơn hàng", styles.headerStyle);
        createCell(headerRow, 1, "Thời gian đặt hàng", styles.headerStyle);
        createCell(headerRow, 2, "Tên người nhận", styles.headerStyle);
        createCell(headerRow, 3, "Số điện thoại", styles.headerStyle);
        createCell(headerRow, 4, "Trạng thái", styles.headerStyle);
        createCell(headerRow, 5, "Thanh toán", styles.headerStyle);
        createCell(headerRow, 6, "Tiền hàng (VND)", styles.headerStyle);
        createCell(headerRow, 7, "Phí ship (VND)", styles.headerStyle);
        createCell(headerRow, 8, "Tổng cộng (VND)", styles.headerStyle);

        int r = 1;
        for (var o : orderRows) {
            var row = sheet.createRow(r++);
            createCell(row, 0, o.code(), styles.dataCenteredStyle);

            var createdStr = o.createdAt().atZone(VN_ZONE).format(DATETIME_FORMATTER);
            createCell(row, 1, createdStr, styles.dataCenteredStyle);

            createCell(row, 2, o.shippingName(), styles.dataStyle);

            String phone = o.shippingPhone();
            if (maskPhone) {
                phone = maskPhoneNumber(phone);
            }
            createCell(row, 3, phone, styles.dataCenteredStyle);

            createCell(row, 4, mapStatusLabel(o.status()), styles.dataCenteredStyle);
            createCell(row, 5, o.paymentMethod(), styles.dataCenteredStyle);
            createNumericCell(row, 6, o.subtotal(), styles.currencyStyle);
            createNumericCell(row, 7, o.shippingFee(), styles.currencyStyle);
            createNumericCell(row, 8, o.total(), styles.currencyStyle);
        }

        autoSizeColumns(sheet, 9);
    }

    private String mapStatusLabel(String status) {
        return switch (status) {
            case "PENDING" -> "Chờ xác nhận";
            case "CONFIRMED" -> "Đã xác nhận";
            case "SHIPPING" -> "Đang giao hàng";
            case "DELIVERED" -> "Giao thành công";
            case "CANCELLED" -> "Đã hủy";
            default -> status;
        };
    }

    private String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone == null ? "" : phone;
        }
        var len = phone.length();
        return phone.substring(0, 3) + "****" + phone.substring(len - 3);
    }

    // ==========================================
    // STYLES & CELL HELPERS
    // ==========================================
    private ReportStyles createReportStyles(XSSFWorkbook workbook) {
        var helper = workbook.getCreationHelper();

        // Fonts
        var fontTitle = workbook.createFont();
        fontTitle.setFontName("Segoe UI");
        fontTitle.setFontHeightInPoints((short) 14);
        fontTitle.setBold(true);

        var fontSection = workbook.createFont();
        fontSection.setFontName("Segoe UI");
        fontSection.setFontHeightInPoints((short) 11);
        fontSection.setBold(true);

        var fontHeader = workbook.createFont();
        fontHeader.setFontName("Segoe UI");
        fontHeader.setFontHeightInPoints((short) 10);
        fontHeader.setBold(true);
        fontHeader.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());

        var fontBold = workbook.createFont();
        fontBold.setFontName("Segoe UI");
        fontBold.setFontHeightInPoints((short) 10);
        fontBold.setBold(true);

        var fontNormal = workbook.createFont();
        fontNormal.setFontName("Segoe UI");
        fontNormal.setFontHeightInPoints((short) 10);

        // Formats
        var currencyFmt = helper.createDataFormat().getFormat("#,##0 \"₫\"");
        var integerFmt = helper.createDataFormat().getFormat("#,##0");
        var percentFmt = helper.createDataFormat().getFormat("0.0%");

        // Colors
        var colorHeader = new XSSFColor(COLOR_INDIGO_HEADER, null);
        var colorLight = new XSSFColor(COLOR_LIGHT_INDIGO, null);
        var colorBorder = new XSSFColor(COLOR_BORDER_GRAY, null);

        // 1. Sheet Title Style
        var sheetTitle = workbook.createCellStyle();
        sheetTitle.setFont(fontTitle);
        sheetTitle.setVerticalAlignment(VerticalAlignment.CENTER);

        // 2. Section Title Style
        var sectionTitle = workbook.createCellStyle();
        sectionTitle.setFont(fontSection);
        sectionTitle.setVerticalAlignment(VerticalAlignment.CENTER);

        // 3. Header Style
        var header = (XSSFCellStyle) workbook.createCellStyle();
        header.setFont(fontHeader);
        header.setFillForegroundColor(colorHeader);
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(header, colorBorder);

        // 4. Data Style
        var data = (XSSFCellStyle) workbook.createCellStyle();
        data.setFont(fontNormal);
        data.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(data, colorBorder);

        var dataCentered = (XSSFCellStyle) workbook.createCellStyle();
        dataCentered.cloneStyleFrom(data);
        dataCentered.setAlignment(HorizontalAlignment.CENTER);

        var currency = (XSSFCellStyle) workbook.createCellStyle();
        currency.cloneStyleFrom(data);
        currency.setDataFormat(currencyFmt);
        currency.setAlignment(HorizontalAlignment.RIGHT);

        var integer = (XSSFCellStyle) workbook.createCellStyle();
        integer.cloneStyleFrom(data);
        integer.setDataFormat(integerFmt);
        integer.setAlignment(HorizontalAlignment.RIGHT);

        var percent = (XSSFCellStyle) workbook.createCellStyle();
        percent.cloneStyleFrom(data);
        percent.setDataFormat(percentFmt);
        percent.setAlignment(HorizontalAlignment.RIGHT);

        // 5. Total Row Styles
        var totalLabel = (XSSFCellStyle) workbook.createCellStyle();
        totalLabel.cloneStyleFrom(data);
        totalLabel.setFont(fontBold);
        totalLabel.setAlignment(HorizontalAlignment.CENTER);
        totalLabel.setFillForegroundColor(colorLight);
        totalLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        var totalCurrency = (XSSFCellStyle) workbook.createCellStyle();
        totalCurrency.cloneStyleFrom(currency);
        totalCurrency.setFont(fontBold);
        totalCurrency.setFillForegroundColor(colorLight);
        totalCurrency.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        var totalInteger = (XSSFCellStyle) workbook.createCellStyle();
        totalInteger.cloneStyleFrom(integer);
        totalInteger.setFont(fontBold);
        totalInteger.setFillForegroundColor(colorLight);
        totalInteger.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // 6. Meta Styles
        var metaLabel = workbook.createCellStyle();
        metaLabel.setFont(fontBold);

        var metaValue = workbook.createCellStyle();
        metaValue.setFont(fontNormal);

        return new ReportStyles(
            sheetTitle,
            sectionTitle,
            header,
            data,
            dataCentered,
            currency,
            integer,
            percent,
            totalLabel,
            totalCurrency,
            totalInteger,
            metaLabel,
            metaValue
        );
    }

    private void setBorder(XSSFCellStyle style, XSSFColor color) {
        style.setBorderTop(BorderStyle.THIN);
        style.setTopBorderColor(color);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBottomBorderColor(color);
        style.setBorderLeft(BorderStyle.THIN);
        style.setLeftBorderColor(color);
        style.setBorderRight(BorderStyle.THIN);
        style.setRightBorderColor(color);
    }

    private void createCell(Row row, int col, String value, CellStyle style) {
        var cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private void createNumericCell(Row row, int col, double value, CellStyle style) {
        var cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void createNumericCell(Row row, int col, long value, CellStyle style) {
        var cell = row.createCell(col);
        cell.setCellValue((double) value);
        cell.setCellStyle(style);
    }

    private void autoSizeColumns(XSSFSheet sheet, int numCols) {
        for (int i = 0; i < numCols; i++) {
            sheet.autoSizeColumn(i);
            // Thêm padding cho các cột
            var currentWidth = sheet.getColumnWidth(i);
            sheet.setColumnWidth(i, Math.min(Math.max(currentWidth + 1200, 3200), 20000));
        }
    }

    private record ReportStyles(
        CellStyle sheetTitleStyle,
        CellStyle sectionTitleStyle,
        CellStyle headerStyle,
        CellStyle dataStyle,
        CellStyle dataCenteredStyle,
        CellStyle currencyStyle,
        CellStyle integerStyle,
        CellStyle percentStyle,
        CellStyle totalLabelStyle,
        CellStyle totalCurrencyStyle,
        CellStyle totalIntegerStyle,
        CellStyle metaLabelStyle,
        CellStyle metaValueStyle
    ) {}
}
