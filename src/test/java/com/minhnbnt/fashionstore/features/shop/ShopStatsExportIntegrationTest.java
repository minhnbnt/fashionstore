package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ghim đường xuất báo cáo doanh thu trên shop thật CHƯA có dữ liệu: chạy đủ
 * service + SQL export (revenueByPeriod, orderStatusSummary, revenueByPayment,
 * productPerformanceForExport, ordersForExport) qua Testcontainers — CSV chỉ
 * có header UTF-8 BOM, Excel đủ 4 sheet chỉ có header, không crash.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ShopStatsExportIntegrationTest {

    @Autowired
    private ShopStatsService stats;

    @Autowired
    private UserService users;

    @Autowired
    private ShopService shops;

    private UUID ownerId;
    private UUID shopId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        users.upsert(ownerId, new CreateUserRequest("export-" + ownerId + "@t.local", "Export Owner", null));

        var adminId = UUID.randomUUID();
        users.upsert(adminId, new CreateUserRequest("export-admin-" + adminId + "@t.local", "Export Admin", null));

        var slug = "export-empty-" + UUID.randomUUID();
        var shop = shops.register(ownerId, ShopTestFixtures.createRequest("Shop " + slug, slug));
        shops.approve(adminId, shop.id());
        shopId = shop.id();
    }

    @Test
    void exportCsvForShopWithoutDataReturnsHeaderOnly() {
        var bytes = stats.exportRevenueReport(ownerId, shopId, "csv", null, null);

        assertThat(bytes).isNotEmpty();

        var csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFNgày,Doanh thu (VND),Số đơn hàng\n");
        assertThat(csv.lines()).hasSize(1);
    }

    @Test
    void exportExcelForShopWithoutDataReturnsWorkbookWithEmptySheets() throws IOException {
        var bytes = stats.exportRevenueReport(ownerId, shopId, "xlsx", null, null);

        assertThat(bytes).isNotEmpty();

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(4);

            var periodSheet = workbook.getSheetAt(1);
            assertThat(periodSheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Ngày");
            assertThat(periodSheet.getLastRowNum()).isZero();

            var ordersSheet = workbook.getSheetAt(3);
            assertThat(ordersSheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Mã đơn hàng");
            assertThat(ordersSheet.getLastRowNum()).isZero();
        }
    }
}
