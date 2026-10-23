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
import java.util.UUID;

@Table("orders")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
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

    private String note;

    private @Nullable String cancelReason;

    private @Nullable String cancelNote;

    private @Nullable OrderCanceller cancelledBy;

    private @Nullable Instant confirmedAt;

    private @Nullable Instant shippedAt;

    private @Nullable Instant deliveredAt;

    private @Nullable Instant receivedAt;

    private @Nullable Instant cancelledAt;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    private boolean newRecord;

    public static Order create(
        String code,
                               UUID shopId,
                               UUID buyerId,
                               long subtotal,
                               long shippingFee,
                               String shippingName,
                               String shippingPhone,
                               String shippingAddress) {
        var now = Instant.now();
        return new Order(
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
            "",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            now,
            now,
            true);
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
        cancelledBy = OrderCanceller.BUYER;
        cancelledAt = Instant.now();
        updatedAt = Instant.now();
    }

    public void cancelBySeller(String reason, @Nullable String note) {
        if (status != OrderStatus.PENDING && status != OrderStatus.CONFIRMED) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Only pending or confirmed orders can be cancelled by seller");
        }
        status = OrderStatus.CANCELLED;
        cancelReason = reason;
        cancelNote = note;
        cancelledBy = OrderCanceller.SELLER;
        cancelledAt = Instant.now();
        updatedAt = Instant.now();
    }

    /**
     * Admin hủy can thiệp (gian lận, shop bỏ mặc đơn...). Như shop, chỉ hủy được khi chưa bàn giao vận chuyển.
     */
    public void cancelByAdmin(AdminCancelReason reason, String note) {
        if (status != OrderStatus.PENDING && status != OrderStatus.CONFIRMED) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Only pending or confirmed orders can be cancelled by admin");
        }
        status = OrderStatus.CANCELLED;
        cancelReason = reason.name();
        cancelNote = note;
        cancelledBy = OrderCanceller.ADMIN;
        cancelledAt = Instant.now();
        updatedAt = Instant.now();
    }

    public void confirmReceipt() {
        if (status != OrderStatus.DELIVERED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only delivered orders can be confirmed");
        }
        receivedAt = Instant.now();
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
