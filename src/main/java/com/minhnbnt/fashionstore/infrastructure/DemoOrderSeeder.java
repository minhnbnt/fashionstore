package com.minhnbnt.fashionstore.infrastructure;

import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.CancelOrderRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutItemRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.OrderService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Seeds demo buyers and orders spread across every seller-facing order status.
 * Package-private collaborator of {@link DemoSeeder}, not a Spring bean.
 * Dev-only: never enabled outside {@code app.demo-seed=true}.
 */
class DemoOrderSeeder {

    private final UserService users;

    private final OrderService orders;

    DemoOrderSeeder(UserService users, OrderService orders) {
        this.users = users;
        this.orders = orders;
    }

    record DemoBuyers(UUID linh, UUID tram, UUID khang, UUID an) {
    }

    record DemoOrders(
        UUID aoThunLinh,
        UUID aoThunTram,
        UUID aoThunKhang,
        UUID quanJeansAn,
        UUID chanVayLinh,
        UUID tuiToteTram,
        UUID aoLenKhang,
        UUID parkaAn,
        UUID vayHoaLinh
    ) {
    }

    DemoBuyers seedBuyers() {
        return new DemoBuyers(
            buyer("linh@demo.local", "Linh, Hà Nội"),
            buyer("tram@demo.local", "Trâm, Sài Gòn"),
            buyer("khang@demo.local", "Khang, Sài Gòn"),
            buyer("an@demo.local", "An, Đà Nẵng")
        );
    }

    DemoOrders seedOrders(UUID ownerId, UUID shopId, Map<String, UUID> variantIdBySku, DemoBuyers buyers) {
        // DELIVERED + received by buyer: eligible for reviews and returns.
        var aoThunLinh = fullFlow(
            ownerId, shopId, buyers.linh(), "Linh, Hà Nội", "0901112223",
            "23 phố Huế, Hoàn Kiếm, Hà Nội", item(variantIdBySku, "AO-THUN-OVERSIZE-M", 1)
        );
        var aoThunTram = fullFlow(
            ownerId, shopId, buyers.tram(), "Trâm, Sài Gòn", "0902223334",
            "45 Lê Lợi, Quận 1, TP. Hồ Chí Minh", item(variantIdBySku, "AO-THUN-OVERSIZE-L", 1)
        );
        var aoThunKhang = fullFlow(
            ownerId, shopId, buyers.khang(), "Khang, Sài Gòn", "0903334445",
            "88 Nguyễn Văn Cừ, TP. Thủ Đức, TP. Hồ Chí Minh", item(variantIdBySku, "AO-THUN-OVERSIZE-M", 1)
        );
        var quanJeansAn = fullFlow(
            ownerId, shopId, buyers.an(), "An, Đà Nẵng", "0904445556",
            "12 Bạch Đằng, Hải Châu, Đà Nẵng", item(variantIdBySku, "QUAN-JEANS-ONG-SUONG-M", 1)
        );
        var chanVayLinh = fullFlow(
            ownerId, shopId, buyers.linh(), "Linh, Hà Nội", "0901112223",
            "23 phố Huế, Hoàn Kiếm, Hà Nội", item(variantIdBySku, "CHAN-VAY-CHU-A-S", 1)
        );

        // PENDING: untouched by the seller.
        var tuiToteTram = checkoutOne(
            shopId, buyers.tram(), "Trâm, Sài Gòn", "0902223334",
            "45 Lê Lợi, Quận 1, TP. Hồ Chí Minh", item(variantIdBySku, "TUI-TOTE-CANVAS-FREE", 1)
        );

        // CANCELLED after confirmation: seller cancels, stock is restored.
        var aoLenKhang = checkoutOne(
            shopId, buyers.khang(), "Khang, Sài Gòn", "0903334445",
            "88 Nguyễn Văn Cừ, TP. Thủ Đức, TP. Hồ Chí Minh", item(variantIdBySku, "AO-LEN-COTTON-M", 1)
        );
        orders.confirm(ownerId, aoLenKhang);
        orders.sellerCancel(ownerId, aoLenKhang, new CancelOrderRequest(
            "OUT_OF_STOCK",
            "Mẫu này vừa hết hàng, shop hoàn tiền cho bạn ngay ạ."
        ));

        // CONFIRMED: waiting for shipment.
        var parkaAn = checkoutOne(
            shopId, buyers.an(), "An, Đà Nẵng", "0904445556",
            "12 Bạch Đằng, Hải Châu, Đà Nẵng", item(variantIdBySku, "AO-KHOAC-PARKA-M", 1)
        );
        orders.confirm(ownerId, parkaAn);

        // SHIPPING: on the way to the buyer.
        var vayHoaLinh = checkoutOne(
            shopId, buyers.linh(), "Linh, Hà Nội", "0901112223",
            "23 phố Huế, Hoàn Kiếm, Hà Nội", item(variantIdBySku, "VAY-HOA-NHI-S", 1)
        );
        orders.confirm(ownerId, vayHoaLinh);
        orders.ship(ownerId, vayHoaLinh);

        return new DemoOrders(
            aoThunLinh,
            aoThunTram,
            aoThunKhang,
            quanJeansAn,
            chanVayLinh,
            tuiToteTram,
            aoLenKhang,
            parkaAn,
            vayHoaLinh
        );
    }

    private UUID buyer(String email, String displayName) {
        var response = users.upsert(UUID.randomUUID(), new CreateUserRequest(email, displayName, null));
        return response.id();
    }

    private CheckoutItemRequest item(Map<String, UUID> variantIdBySku, String sku, int quantity) {
        var variantId = variantIdBySku.get(sku);
        if (variantId == null) {
            throw new IllegalStateException("Missing demo variant: " + sku);
        }
        return new CheckoutItemRequest(variantId, quantity);
    }

    private UUID checkoutOne(
        UUID shopId,
        UUID buyerId,
        String name,
        String phone,
        String address,
        CheckoutItemRequest... items
    ) {
        var order = orders.checkout(buyerId, new CheckoutRequest(shopId, List.of(items), name, phone, address, 0L));
        return order.id();
    }

    private UUID fullFlow(
        UUID ownerId,
        UUID shopId,
        UUID buyerId,
        String name,
        String phone,
        String address,
        CheckoutItemRequest... items
    ) {
        var orderId = checkoutOne(shopId, buyerId, name, phone, address, items);
        orders.confirm(ownerId, orderId);
        orders.ship(ownerId, orderId);
        orders.deliver(ownerId, orderId);
        orders.confirmReceipt(buyerId, orderId);
        return orderId;
    }
}
