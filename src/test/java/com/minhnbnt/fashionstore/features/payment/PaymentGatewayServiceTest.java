package com.minhnbnt.fashionstore.features.payment;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.payment.PaymentGatewayResponse;
import com.minhnbnt.fashionstore.dto.payment.PaymentMethodOption;
import com.minhnbnt.fashionstore.dto.payment.UpdatePaymentGatewayRequest;
import com.minhnbnt.fashionstore.dto.settings.UpdateSettingsRequest;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.settings.ConfigAuditService;
import com.minhnbnt.fashionstore.features.settings.SystemSettingsService;
import com.minhnbnt.fashionstore.model.ConfigAuditEntity;
import com.minhnbnt.fashionstore.model.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "test.isolation=PaymentGatewayServiceTest")
class PaymentGatewayServiceTest {

    @Autowired
    private PaymentGatewayService gateways;

    @Autowired
    private SystemSettingsService settings;

    @Autowired
    private ConfigAuditService audits;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcClient jdbc;

    private UUID adminId;

    @BeforeEach
    void resetState() {
        jdbc.sql("DELETE FROM admin_config_audit").update();
        jdbc.sql("""
            UPDATE payment_gateway
            SET name = 'Thanh toán khi nhận hàng (COD)',
                enabled = TRUE,
                bank_bin = NULL,
                bank_code = NULL,
                bank_name = NULL,
                account_number = NULL,
                account_name = NULL,
                webhook_secret = NULL,
                sandbox_mode = FALSE,
                updated_by = NULL
            WHERE code = 'COD'
            """).update();
        jdbc.sql("""
            UPDATE payment_gateway
            SET name = 'Chuyển khoản ngân hàng (VietQR)',
                enabled = TRUE,
                bank_bin = '970422',
                bank_code = 'MB',
                bank_name = 'Ngân hàng TMCP Quân đội (MB Bank)',
                account_number = '0900123456789',
                account_name = 'SAN TMDT FASHIONSTORE',
                webhook_secret = 'demo-vietqr-secret-key',
                sandbox_mode = TRUE,
                updated_by = NULL
            WHERE code = 'VIETQR'
            """).update();
        jdbc.sql("UPDATE system_setting SET value = '0' WHERE key = 'order.cod_min_total'").update();
        jdbc.sql("UPDATE system_setting SET value = '10000000' WHERE key = 'order.cod_max_total'").update();
        jdbc.sql("UPDATE system_setting SET value = '15' WHERE key = 'order.payment_hold_minutes'").update();

        adminId = UUID.randomUUID();
        users.upsert(adminId, new CreateUserRequest("admin-" + adminId + "@t.local", "Admin"));
    }

    @Test
    void seededGatewaysIncludeCodAndVietQrWithoutExposingSecret() {
        var list = gateways.list();
        assertThat(list).extracting(PaymentGatewayResponse::code).containsExactly("COD", "VIETQR");
        var vietQr = list.get(1);
        assertThat(vietQr.enabled()).isTrue();
        assertThat(vietQr.webhookSecretSet()).isTrue();
        assertThat(vietQr.bankCode()).isEqualTo("MB");
    }

    @Test
    void updateVietQrValidatesBankBinAndKeepsOldSecretWhenBlank() {
        var updated = gateways.update(
            adminId,
            PaymentMethod.VIETQR,
            new UpdatePaymentGatewayRequest(
                true,
                "VietQR NAPAS",
                "970436",
                "0011223344",
                "CONG TY DHMQ",
                "",
                false
            )
        );
        assertThat(updated.bankCode()).isEqualTo("VCB");
        assertThat(updated.webhookSecretSet()).isTrue();
        assertThat(gateways.requireActiveVietQrGateway().getWebhookSecret()).isEqualTo("demo-vietqr-secret-key");

        var auditItems = audits.page(ConfigAuditEntity.PAYMENT_GATEWAY, 0, 10).items();
        assertThat(auditItems).hasSize(1);
        assertThat(auditItems.getFirst().newValue()).contains("****");
        assertThat(auditItems.getFirst().newValue()).doesNotContain("demo-vietqr-secret-key");
    }

    @Test
    void updateVietQrWithInvalidBankBinIsRejected() {
        assertThatThrownBy(() -> gateways.update(
            adminId,
            PaymentMethod.VIETQR,
            new UpdatePaymentGatewayRequest(true, null, "999999", null, null, null, null)
        ))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void enablingVietQrWithBlankAccountNumberIsRejectedWithConflict() {
        assertThatThrownBy(() -> gateways.update(
            adminId,
            PaymentMethod.VIETQR,
            new UpdatePaymentGatewayRequest(true, null, null, "   ", null, null, null)
        ))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cannotDisableLastEnabledGateway() {
        gateways.update(
            adminId,
            PaymentMethod.VIETQR,
            new UpdatePaymentGatewayRequest(false, null, null, null, null, null, null)
        );
        assertThatThrownBy(() -> gateways.update(
            adminId,
            PaymentMethod.COD,
            new UpdatePaymentGatewayRequest(false, null, null, null, null, null, null)
        ))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void requireAvailableEnforcesCodLimitsAndDisabledGateway() {
        settings.update(
            adminId,
            new UpdateSettingsRequest(null, null, null, null, null, null, null, null, null, 100_000L, 500_000L)
        );
        assertThatThrownBy(() -> gateways.requireAvailable(PaymentMethod.COD, 50_000L))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        assertThatThrownBy(() -> gateways.requireAvailable(PaymentMethod.COD, 600_000L))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        gateways.requireAvailable(PaymentMethod.COD, 300_000L);

        gateways.update(
            adminId,
            PaymentMethod.VIETQR,
            new UpdatePaymentGatewayRequest(false, null, null, null, null, null, null)
        );
        assertThatThrownBy(() -> gateways.requireAvailable(PaymentMethod.VIETQR, 300_000L))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void publicMethodsReturnsEnabledGatewaysAndCodSettings() {
        var publicView = gateways.publicMethods();
        assertThat(publicView.methods())
            .extracting(PaymentMethodOption::code)
            .containsExactly("COD", "VIETQR");
        assertThat(publicView.codMaxTotal()).isEqualTo(10_000_000L);
        assertThat(publicView.paymentHoldMinutes()).isEqualTo(15);
    }
}
