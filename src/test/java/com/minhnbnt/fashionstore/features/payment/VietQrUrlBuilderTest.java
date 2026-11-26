package com.minhnbnt.fashionstore.features.payment;

import com.minhnbnt.fashionstore.model.PaymentTransaction;
import com.minhnbnt.fashionstore.model.PaymentTransactionStatus;
import com.minhnbnt.fashionstore.model.RefundStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VietQrUrlBuilderTest {

    @Test
    void generateAndExtractTxnRefFromBankTransferDescription() {
        var ref = VietQrUrlBuilder.generateTxnRef();
        assertThat(ref).matches("^DHMQ[A-Z0-9]{10}$");
        assertThat(
            VietQrUrlBuilder.extractTxnRef("MBVCB.12345.chuyen tien " + ref.toLowerCase(Locale.ROOT) + "CT tu 0123")
        ).isEqualTo(ref);
        assertThat(VietQrUrlBuilder.extractTxnRef("Chuyen khoan khong co ma")).isNull();
        assertThat(VietQrUrlBuilder.extractTxnRef(null)).isNull();
        assertThat(VietQrUrlBuilder.extractTxnRef("   ")).isNull();
    }

    @Test
    void buildQrImageUrlEncodesAccountNameAndLocksAmountAndAddInfo() {
        var url = VietQrUrlBuilder.buildQrImageUrl(
            "970422",
            "0900123456789",
            "SAN TMDT FASHIONSTORE",
            350_000L,
            "DHMQ1234567890"
        );
        assertThat(url).isEqualTo(
            "https://img.vietqr.io/image/970422-0900123456789-compact2.png"
                + "?amount=350000&addInfo=DHMQ1234567890&accountName=SAN+TMDT+FASHIONSTORE"
        );
    }

    @Test
    void paymentTransactionDomainMethodsHandleUnderpaidOverpaidAndRefundRules() {
        var now = Instant.now();
        var tx = PaymentTransaction.create(
            UUID.randomUUID(),
            "DHMQ1234567890",
            200_000L,
            "970422",
            "MB",
            "0900123456789",
            "SAN TMDT FASHIONSTORE",
            "https://img.vietqr.io/image/970422-0900123456789-compact2.png",
            now.plusSeconds(900),
            now
        );

        assertThatThrownBy(() -> tx.markRefunded(UUID.randomUUID(), "Ghi chu", now))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        tx.markUnderpaid(150_000L, "FT1", "CK DHMQ1234567890", "{}", now);
        assertThat(tx.getStatus()).isEqualTo(PaymentTransactionStatus.UNDERPAID);
        assertThat(tx.getReceivedAmount()).isEqualTo(150_000L);
        assertThat(tx.getRefundStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(tx.getRefundAmount()).isEqualTo(150_000L);
        assertThat(tx.getRefundReason()).isEqualTo("Khách chuyển thiếu tiền (150000/200000₫)");

        var overpaidTx = PaymentTransaction.create(
            UUID.randomUUID(),
            "DHMQ0987654321",
            200_000L,
            "970422",
            "MB",
            "0900123456789",
            "SAN TMDT FASHIONSTORE",
            "https://img.vietqr.io/image/970422-0900123456789-compact2.png",
            now.plusSeconds(900),
            now
        );
        overpaidTx.markSuccess(250_000L, "FT2", "CK DHMQ0987654321", "{}", now);
        assertThat(overpaidTx.getStatus()).isEqualTo(PaymentTransactionStatus.SUCCESS);
        assertThat(overpaidTx.getRefundStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(overpaidTx.getRefundAmount()).isEqualTo(50_000L);
        assertThat(overpaidTx.getRefundReason()).isEqualTo("Khách chuyển dư tiền");
    }
}
