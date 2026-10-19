package com.minhnbnt.fashionstore.model;

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
import java.util.UUID;

@Table("orders")
@Getter
public class Order implements Persistable<UUID> {

    @Id
    private UUID id;

    private String code;

    private UUID shopId;

    private UUID buyerId;

    private OrderStatus status;

    private String paymentMethod;

    private PaymentStatus paymentStatus;

    private long subtotal;

    private long shippingFee;

    private long total;

    private String shippingName;

    private String shippingPhone;

    private String shippingAddress;

    private @Nullable UUID addressId;

    private String note;

    private @Nullable Instant confirmedAt;

    private @Nullable Instant shippedAt;

    private @Nullable Instant deliveredAt;

    private @Nullable Instant cancelledAt;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    private boolean newRecord;

    @PersistenceCreator
    public Order(
        UUID id,
        String code,
        UUID shopId,
        UUID buyerId,
        OrderStatus status,
        String paymentMethod,
        PaymentStatus paymentStatus,
        long subtotal,
        long shippingFee,
        long total,
        String shippingName,
        String shippingPhone,
        String shippingAddress,
        @Nullable UUID addressId,
        String note,
        @Nullable Instant confirmedAt,
        @Nullable Instant shippedAt,
        @Nullable Instant deliveredAt,
        @Nullable Instant cancelledAt,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = id;
        this.code = code;
        this.shopId = shopId;
        this.buyerId = buyerId;
        this.status = status;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = paymentStatus;
        this.subtotal = subtotal;
        this.shippingFee = shippingFee;
        this.total = total;
        this.shippingName = shippingName;
        this.shippingPhone = shippingPhone;
        this.shippingAddress = shippingAddress;
        this.addressId = addressId;
        this.note = note;
        this.confirmedAt = confirmedAt;
        this.shippedAt = shippedAt;
        this.deliveredAt = deliveredAt;
        this.cancelledAt = cancelledAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.newRecord = false;
    }

    public static Order create(
        String code,
        UUID shopId,
        UUID buyerId,
        long subtotal,
        long shippingFee,
        String shippingName,
        String shippingPhone,
        String shippingAddress,
        @Nullable UUID addressId
    ) {
        var now = Instant.now();
        var order = new Order(
            UUID.randomUUID(),
            code,
            shopId,
            buyerId,
            OrderStatus.PENDING,
            "COD",
            PaymentStatus.UNPAID,
            subtotal,
            shippingFee,
            subtotal + shippingFee,
            shippingName,
            shippingPhone,
            shippingAddress,
            addressId,
            "",
            null,
            null,
            null,
            null,
            now,
            now
        );
        order.newRecord = true;
        return order;
    }

    public void confirm() {
        if (status != OrderStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending orders can be confirmed");
        }
        status = OrderStatus.CONFIRMED;
        confirmedAt = Instant.now();
        updatedAt = Instant.now();
    }

    public void ship() {
        if (status != OrderStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only confirmed orders can be shipped");
        }
        status = OrderStatus.SHIPPING;
        shippedAt = Instant.now();
        updatedAt = Instant.now();
    }

    public void deliver() {
        if (status != OrderStatus.SHIPPING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only shipping orders can be delivered");
        }
        status = OrderStatus.DELIVERED;
        paymentStatus = PaymentStatus.COLLECTED;
        deliveredAt = Instant.now();
        updatedAt = Instant.now();
    }

    public void cancel() {
        if (status != OrderStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending orders can be cancelled");
        }
        status = OrderStatus.CANCELLED;
        cancelledAt = Instant.now();
        updatedAt = Instant.now();
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newRecord;
    }
}
