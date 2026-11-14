package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutItemRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.dto.order.CreateReturnRequest;
import com.minhnbnt.fashionstore.dto.order.RejectReturnRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.notification.NotificationRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.test.ProductTestFixtures;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderAutoCompleteJobTest {

    @Autowired
    private OrderAutoCompleteJob job;

    @Autowired
    private OrderService orders;

    @Autowired
    private ReturnRequestService returnRequests;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private NotificationRepository notifications;

    private UUID mirroredUser() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("ac-" + id + "@t.local", "U", null));
        return id;
    }

    @Test
    void autoCompletesExpiredDeliveredOrdersUnlessBlockingReturnExists() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var adminId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shop = shops.register(ownerId, ShopTestFixtures.createRequest("Shop AC " + suffix, "ac-" + suffix));
        shops.approve(adminId, shop.id());

        var product = catalog.createProduct(
            ownerId,
            shop.id(),
            ProductTestFixtures.request(
                "Ao AC " + suffix,
                "ao-ac-" + suffix,
                null,
                null,
                List.of(new CreateVariantRequest("SKU-AC-" + suffix, "L", "Trang", 100000L, null, 20, true))
            )
        );
        var variantId = catalog.getDetail(product.id()).variants().getFirst().id();

        var dueOrder = orders.checkout(
            buyerId,
            new CheckoutRequest(shop.id(), List.of(new CheckoutItemRequest(variantId, 1)), "An", "090", "HN", 0L)
        );
        orders.confirm(ownerId, dueOrder.id());
        orders.ship(ownerId, dueOrder.id(), null);
        orders.deliver(ownerId, dueOrder.id());

        var blockedOrder = orders.checkout(
            buyerId,
            new CheckoutRequest(shop.id(), List.of(new CheckoutItemRequest(variantId, 1)), "An", "090", "HN", 0L)
        );
        orders.confirm(ownerId, blockedOrder.id());
        orders.ship(ownerId, blockedOrder.id(), null);
        orders.deliver(ownerId, blockedOrder.id());
        returnRequests.create(buyerId, blockedOrder.id(), new CreateReturnRequest("DAMAGED", "Loi", List.of()));

        var rejectedThenDue = orders.checkout(
            buyerId,
            new CheckoutRequest(shop.id(), List.of(new CheckoutItemRequest(variantId, 1)), "An", "090", "HN", 0L)
        );
        orders.confirm(ownerId, rejectedThenDue.id());
        orders.ship(ownerId, rejectedThenDue.id(), null);
        orders.deliver(ownerId, rejectedThenDue.id());
        var rejected = returnRequests.create(
            buyerId, rejectedThenDue.id(), new CreateReturnRequest("CHANGE_MIND", "Doi y", List.of())
        );
        returnRequests.reject(ownerId, rejected.id(), new RejectReturnRequest("Khong ho tro"));

        var eightDaysAgo = Timestamp.from(Instant.now().minus(8, ChronoUnit.DAYS));
        jdbc.sql("UPDATE orders SET delivered_at = ? WHERE id IN (?, ?, ?)")
            .params(eightDaysAgo, dueOrder.id(), blockedOrder.id(), rejectedThenDue.id())
            .update();

        job.autoCompleteDeliveredOrders();

        assertThat(orders.getDetail(buyerId, dueOrder.id()).receivedAt()).isNotNull();
        assertThat(orders.getDetail(buyerId, rejectedThenDue.id()).receivedAt()).isNotNull();
        assertThat(orders.getDetail(buyerId, blockedOrder.id()).receivedAt()).isNull();

        var completedNotifications = notifications.findPage(buyerId, List.of("ORDER"), 0, 10).stream()
            .filter(n -> "ORDER_COMPLETED".equals(n.type()) && ("/account/orders/" + dueOrder.id()).equals(n.link()))
            .toList();
        assertThat(completedNotifications).hasSize(1);
        assertThat(completedNotifications.getFirst().title())
            .isEqualTo("Đơn #" + dueOrder.code() + " đã hoàn thành");
        assertThat(completedNotifications.getFirst().body()).isEqualTo(
            "Hệ thống đã tự động xác nhận hoàn thành đơn #"
                + dueOrder.code() + " sau 7 ngày giao hàng."
        );
    }
}
