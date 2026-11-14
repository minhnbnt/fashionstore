package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutItemRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.features.order.repository.OrderTrackingEventRepository;
import com.minhnbnt.fashionstore.features.order.repository.ReturnRequestRepository;
import com.minhnbnt.fashionstore.features.review.repository.ReviewRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.Order;
import com.minhnbnt.fashionstore.model.OrderTrackingEvent;
import com.minhnbnt.fashionstore.model.ReturnRequest;
import com.minhnbnt.fashionstore.model.Review;
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
class OrderTrackingRepositoryTest {

    @Autowired
    private OrderRepository orders;

    @Autowired
    private OrderTrackingEventRepository trackingEvents;

    @Autowired
    private ReturnRequestRepository returns;

    @Autowired
    private ReviewRepository reviews;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcClient jdbc;

    private UUID mirroredUser() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("tr-" + id + "@t.local", "U", null));
        return id;
    }

    @Test
    void persistsTrackingEventsAutoCompleteCandidatesAndBuyerReviews() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var adminId = mirroredUser();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var shop = shops.register(ownerId, ShopTestFixtures.createRequest("Shop " + suffix, "s-" + suffix));
        shops.approve(adminId, shop.id());

        var product = catalog.createProduct(
            ownerId,
            shop.id(),
            ProductTestFixtures.request(
                "Ao tracking " + suffix,
                "ao-tr-" + suffix,
                null,
                null,
                List.of(new CreateVariantRequest("SKU-TR-" + suffix, "M", "Den", 120000L, null, 10, true))
            )
        );
        var variantId = catalog.getDetail(product.id()).variants().get(0).id();

        var created = orderService.checkout(
            buyerId,
            new CheckoutRequest(shop.id(), List.of(new CheckoutItemRequest(variantId, 1)), "An", "090", "HN", 0L)
        );
        orderService.confirm(ownerId, created.id());
        orderService.ship(ownerId, created.id());
        orderService.deliver(ownerId, created.id());

        var now = Instant.now();
        trackingEvents.save(
            OrderTrackingEvent.record(
                created.id(),
                "SORTING_FACILITY",
                "Da den kho trung chuyen",
                "Kho Ha Noi",
                "GHN tiep nhan",
                now.plusSeconds(10),
                ownerId
            )
        );

        var loadedEvents = trackingEvents.findByOrderIdOrderByEventTimeDescCreatedAtDesc(created.id());
        assertThat(loadedEvents).hasSize(3);
        assertThat(loadedEvents.get(0).getStatusCode()).isEqualTo("SORTING_FACILITY");

        jdbc.sql("UPDATE orders SET delivered_at = ? WHERE id = ?")
            .params(Timestamp.from(now.minus(8, ChronoUnit.DAYS)), created.id())
            .update();

        var ready = orders.findDeliveredReadyForAutoComplete(now.minus(7, ChronoUnit.DAYS));
        assertThat(ready).extracting(Order::getId).contains(created.id());

        returns.save(ReturnRequest.create(created.id(), shop.id(), buyerId, "DAMAGED", "Loi", new String[0]));
        var afterReturn = orders.findDeliveredReadyForAutoComplete(now.minus(7, ChronoUnit.DAYS));
        assertThat(afterReturn).noneMatch(o -> o.getId().equals(created.id()));

        var latestReturns = returns.findByOrderIdInOrderByCreatedAtDesc(List.of(created.id()));
        assertThat(latestReturns).hasSize(1);

        var r = Review.create(
            product.id(),
            buyerId,
            created.id(),
            "An",
            "M",
            5,
            "San pham rat tot va dep",
            new String[0],
            null,
            new String[0],
            false
        );
        r.update(4, "Sua lai nhan xet chi tiet", new String[0], null, new String[0], false);
        reviews.save(r);

        var mine = reviews.findByBuyerIdOrderByUpdatedAtDescCreatedAtDesc(buyerId);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).getEditedAt()).isNotNull();
    }
}
