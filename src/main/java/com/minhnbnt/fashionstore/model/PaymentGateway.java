package com.minhnbnt.fashionstore.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("payment_gateway")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class PaymentGateway implements Persistable<String> {

    public static final String INSECURE_DEMO_WEBHOOK_SECRET = "demo-vietqr-secret-key";

    @Id
    private String code;

    private String name;

    private boolean enabled;

    private @Nullable String bankBin;

    private @Nullable String bankCode;

    private @Nullable String bankName;

    private @Nullable String accountNumber;

    private @Nullable String accountName;

    private @Nullable String webhookSecret;

    private boolean sandboxMode;

    private Instant updatedAt;

    private @Nullable UUID updatedBy;

    @Transient
    private boolean newRecord;

    public boolean hasWebhookSecret() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    public boolean hasCustomWebhookSecret() {
        return hasWebhookSecret() && !INSECURE_DEMO_WEBHOOK_SECRET.equals(webhookSecret);
    }

    public boolean hasCompleteVietQrBankConfig() {
        if (bankBin == null || bankBin.isBlank()) {
            return false;
        }
        if (accountNumber == null || accountNumber.isBlank()) {
            return false;
        }
        if (accountName == null || accountName.isBlank()) {
            return false;
        }
        return hasWebhookSecret();
    }

    public void applyUpdate(
        String name,
        boolean enabled,
        @Nullable String bankBin,
        @Nullable String bankCode,
        @Nullable String bankName,
        @Nullable String accountNumber,
        @Nullable String accountName,
        @Nullable String webhookSecret,
        boolean sandboxMode,
        UUID updatedBy,
        Instant updatedAt
    ) {
        this.name = name;
        this.enabled = enabled;
        this.bankBin = bankBin;
        this.bankCode = bankCode;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.accountName = accountName;
        this.webhookSecret = webhookSecret;
        this.sandboxMode = sandboxMode;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    @Override
    public String getId() {
        return code;
    }

    @Override
    public boolean isNew() {
        return newRecord;
    }
}
