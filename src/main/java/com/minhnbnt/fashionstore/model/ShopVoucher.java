package com.minhnbnt.fashionstore.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Table("shop_voucher")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class ShopVoucher implements Persistable<UUID> {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9]{4,20}$");

    @Id
    private UUID id;

    private UUID shopId;

    private String code;

    private String name;

    private String description;

    private VoucherDiscountType discountType;

    private long discountValue;

    private @Nullable Long maxDiscountAmount;

    private long minOrderSubtotal;

    private VoucherProductScope productScope;

    private VoucherVisibility visibility;

    private int usageLimit;

    private int usedCount;

    private int perBuyerLimit;

    private Instant startsAt;

    private Instant endsAt;

    private boolean active;

    private @Nullable UUID createdBy;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    private boolean newRecord;

    public static ShopVoucher create(
        UUID shopId,
        String code,
        String name,
        String description,
        VoucherDiscountType discountType,
        long discountValue,
        @Nullable Long maxDiscountAmount,
        long minOrderSubtotal,
        VoucherProductScope productScope,
        VoucherVisibility visibility,
        int usageLimit,
        int perBuyerLimit,
        Instant startsAt,
        Instant endsAt,
        boolean active,
        @Nullable UUID createdBy,
        Instant now
    ) {
        var normalizedCode = normalizeCode(code);
        var normalizedName = normalizeName(name);
        var normalizedDescription = description.trim();
        var normalizedMaxDiscount = normalizeMaxDiscount(discountType, maxDiscountAmount);
        validateTerms(
            normalizedCode,
            normalizedName,
            discountType,
            discountValue,
            normalizedMaxDiscount,
            minOrderSubtotal,
            usageLimit,
            perBuyerLimit,
            startsAt,
            endsAt
        );

        return new ShopVoucher(
            UUID.randomUUID(),
            shopId,
            normalizedCode,
            normalizedName,
            normalizedDescription,
            discountType,
            discountValue,
            normalizedMaxDiscount,
            minOrderSubtotal,
            productScope,
            visibility,
            usageLimit,
            0,
            perBuyerLimit,
            startsAt,
            endsAt,
            active,
            createdBy,
            now,
            now,
            true
        );
    }

    public VoucherStatus statusAt(Instant now) {
        if (!this.active) {
            return VoucherStatus.PAUSED;
        }
        if (!now.isBefore(this.endsAt)) {
            return VoucherStatus.EXPIRED;
        }
        if (this.usedCount >= this.usageLimit) {
            return VoucherStatus.DEPLETED;
        }
        if (now.isBefore(this.startsAt)) {
            return VoucherStatus.UPCOMING;
        }
        return VoucherStatus.ONGOING;
    }

    public long calculateDiscount(long eligibleSubtotal) {
        if (eligibleSubtotal <= 0L) {
            return 0L;
        }

        long raw;
        if (this.discountType == VoucherDiscountType.FIXED_AMOUNT) {
            raw = this.discountValue;
        } else {
            raw = (eligibleSubtotal * this.discountValue) / 100L;
            if (this.maxDiscountAmount != null) {
                raw = Math.min(raw, this.maxDiscountAmount);
            }
        }

        return Math.min(raw, eligibleSubtotal);
    }

    public void update(
        String code,
        String name,
        String description,
        VoucherDiscountType discountType,
        long discountValue,
        @Nullable Long maxDiscountAmount,
        long minOrderSubtotal,
        VoucherProductScope productScope,
        VoucherVisibility visibility,
        int usageLimit,
        int perBuyerLimit,
        Instant startsAt,
        Instant endsAt,
        boolean active,
        Instant now
    ) {
        var normalizedCode = normalizeCode(code);
        var normalizedName = normalizeName(name);
        var normalizedDescription = description.trim();
        var normalizedMaxDiscount = normalizeMaxDiscount(discountType, maxDiscountAmount);

        if (this.usedCount > 0) {
            var coreChanged = !this.code.equals(normalizedCode)
                || this.discountType != discountType
                || this.discountValue != discountValue
                || !Objects.equals(this.maxDiscountAmount, normalizedMaxDiscount)
                || this.minOrderSubtotal != minOrderSubtotal
                || this.productScope != productScope
                || !this.startsAt.equals(startsAt);
            if (coreChanged) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot modify core discount terms of a voucher that has already been used"
                );
            }
            if (usageLimit < this.usedCount) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Usage limit cannot be less than current used count"
                );
            }
        }

        validateTerms(
            normalizedCode,
            normalizedName,
            discountType,
            discountValue,
            normalizedMaxDiscount,
            minOrderSubtotal,
            usageLimit,
            perBuyerLimit,
            startsAt,
            endsAt
        );

        this.code = normalizedCode;
        this.name = normalizedName;
        this.description = normalizedDescription;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxDiscountAmount = normalizedMaxDiscount;
        this.minOrderSubtotal = minOrderSubtotal;
        this.productScope = productScope;
        this.visibility = visibility;
        this.usageLimit = usageLimit;
        this.perBuyerLimit = perBuyerLimit;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.active = active;
        this.updatedAt = now;
    }

    public void setActive(boolean active, Instant now) {
        this.active = active;
        this.updatedAt = now;
    }

    public void incrementUsedCount(Instant now) {
        if (this.usedCount >= this.usageLimit) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Voucher usage limit has been reached");
        }
        this.usedCount = this.usedCount + 1;
        this.updatedAt = now;
    }

    public void markPersisted() {
        this.newRecord = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newRecord;
    }

    private static String normalizeCode(String rawCode) {
        return rawCode.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeName(String rawName) {
        return rawName.trim();
    }

    private static @Nullable Long normalizeMaxDiscount(
        VoucherDiscountType discountType,
        @Nullable Long maxDiscountAmount
    ) {
        if (discountType == VoucherDiscountType.FIXED_AMOUNT) {
            return null;
        }
        return maxDiscountAmount;
    }

    private static void validateTerms(
        String normalizedCode,
        String normalizedName,
        VoucherDiscountType discountType,
        long discountValue,
        @Nullable Long normalizedMaxDiscount,
        long minOrderSubtotal,
        int usageLimit,
        int perBuyerLimit,
        Instant startsAt,
        Instant endsAt
    ) {
        if (!CODE_PATTERN.matcher(normalizedCode).matches()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Voucher code must be 4-20 alphanumeric characters"
            );
        }
        if (normalizedName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher name must not be blank");
        }
        if (discountValue <= 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Discount value must be positive");
        }
        if (discountType == VoucherDiscountType.PERCENTAGE && (discountValue < 1L || discountValue > 99L)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Percentage discount value must be between 1 and 99"
            );
        }
        if (normalizedMaxDiscount != null && normalizedMaxDiscount <= 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Max discount amount must be positive");
        }
        if (minOrderSubtotal < 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Minimum order subtotal cannot be negative");
        }
        if (usageLimit <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usage limit must be positive");
        }
        if (perBuyerLimit <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Per-buyer limit must be positive");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End time must be after start time");
        }
    }
}
