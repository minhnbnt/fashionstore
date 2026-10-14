package com.minhnbnt.fashionstore.features.address;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.address.AddressRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.features.identity.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AddressServiceTest {

    @Autowired
    private AddressService addresses;

    @Autowired
    private UserService users;

    private UUID user() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("u-" + id + "@t.local", "U", null));
        return id;
    }

    private static AddressRequest address(String city) {
        return new AddressRequest(null, null, null, null, city);
    }

    private static void assertStatus(ResponseStatusException e, HttpStatus status) {
        assertThat(e.getStatusCode()).isEqualTo(status);
    }

    @Test
    void firstAddressBecomesDefault() {
        var userId = user();

        var created = addresses.addAddress(userId, address("Ha Noi"));

        assertThat(created.defaultAddress()).isTrue();
        assertThat(created.city()).isEqualTo("Ha Noi");
    }

    @Test
    void secondAddressIsNotDefault() {
        var userId = user();
        addresses.addAddress(userId, address("Ha Noi"));

        var created = addresses.addAddress(userId, address("Hue"));

        assertThat(created.defaultAddress()).isFalse();

        var list = addresses.getAddresses(userId);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).defaultAddress()).isTrue();
    }

    @Test
    void editsMergeKeepingEmpty() {
        var userId = user();
        var id = addresses.addAddress(
            userId,
            new AddressRequest("12", "Nguyen Trai", "Ben Thanh", "Quan 1", "Sai Gon")
        ).id();

        var updated = addresses.updateAddress(userId, id, new AddressRequest(null, null, null, null, "Da Nang"));

        assertThat(updated.city()).isEqualTo("Da Nang");
        assertThat(updated.houseNumber()).isEqualTo("12");
        assertThat(updated.street()).isEqualTo("Nguyen Trai");
        assertThat(updated.ward()).isEqualTo("Ben Thanh");
        assertThat(updated.district()).isEqualTo("Quan 1");
    }

    @Test
    void blankKeepsPreviousValue() {
        var userId = user();
        var id = addresses.addAddress(
            userId,
            new AddressRequest("12", "Nguyen Trai", null, null, "Sai Gon")
        ).id();

        var updated = addresses.updateAddress(userId, id, new AddressRequest(null, "", null, null, null));

        assertThat(updated.street()).isEqualTo("Nguyen Trai");
    }

    @Test
    void editsDefaultKeepsItDefault() {
        var userId = user();
        var id = addresses.addAddress(userId, address("Ha Noi")).id();

        var updated = addresses.updateAddress(userId, id, address("Hue"));

        assertThat(updated.city()).isEqualTo("Hue");
        assertThat(updated.defaultAddress()).isTrue();
    }

    @Test
    void changesDefault() {
        var userId = user();
        addresses.addAddress(userId, address("Ha Noi"));
        var id = addresses.addAddress(userId, address("Hue")).id();

        var updated = addresses.changeDefaultAddress(userId, id);

        assertThat(updated.defaultAddress()).isTrue();

        var list = addresses.getAddresses(userId);
        assertThat(list.get(0).defaultAddress()).isFalse();
        assertThat(list.get(1).defaultAddress()).isTrue();
    }

    @Test
    void deletingDefaultPromotesEarliestSurvivor() {
        var userId = user();
        var first = addresses.addAddress(userId, address("Ha Noi")).id();
        addresses.addAddress(userId, address("Hue"));
        addresses.addAddress(userId, address("Sai Gon"));

        addresses.deleteAddress(userId, first);

        var survivors = addresses.getAddresses(userId);
        assertThat(survivors).hasSize(2);
        assertThat(survivors.get(0).defaultAddress()).isTrue();
        assertThat(survivors.get(1).defaultAddress()).isFalse();
    }

    @Test
    void deletingLastLeavesListEmpty() {
        var userId = user();
        var id = addresses.addAddress(userId, address("Ha Noi")).id();

        addresses.deleteAddress(userId, id);

        assertThat(addresses.getAddresses(userId)).isEmpty();
    }

    @Test
    void unknownAddressFails() {
        var userId = user();
        var id = UUID.randomUUID();

        assertThatThrownBy(() -> addresses.updateAddress(userId, id, address("Hue")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> addresses.deleteAddress(userId, id))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> addresses.changeDefaultAddress(userId, id))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));
    }

    @Test
    void otherUsersAddressFails() {
        var userId = user();
        var otherId = user();
        var id = addresses.addAddress(otherId, address("Hue")).id();

        assertThatThrownBy(() -> addresses.updateAddress(userId, id, address("Da Nang")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> addresses.deleteAddress(userId, id))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> addresses.changeDefaultAddress(userId, id))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));
    }
}
