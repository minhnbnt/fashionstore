package com.minhnbnt.fashionstore.features.address;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.address.AddressRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.model.AddressLabel;
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

    private static AddressRequest address(String fullAddress) {
        return new AddressRequest("Nguyen Van A", "0912345678", fullAddress, AddressLabel.HOME, false);
    }

    private static void assertStatus(ResponseStatusException e, HttpStatus status) {
        assertThat(e.getStatusCode()).isEqualTo(status);
    }

    @Test
    void firstAddressBecomesDefault() {
        var userId = user();

        var created = addresses.addAddress(userId, address("So 18 Duy Tan, Phuong Dich Vong, Ha Noi"));

        assertThat(created.defaultAddress()).isTrue();
        assertThat(created.fullAddress()).isEqualTo("So 18 Duy Tan, Phuong Dich Vong, Ha Noi");
        assertThat(created.recipientName()).isEqualTo("Nguyen Van A");
        assertThat(created.phoneNumber()).isEqualTo("0912345678");
        assertThat(created.label()).isEqualTo(AddressLabel.HOME);
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
    void updateReplacesAllFields() {
        var userId = user();
        var id = addresses.addAddress(
            userId,
            new AddressRequest(
                "Nguyen Van A",
                "0912345678",
                "12 Nguyen Trai, Phuong Ben Thanh, Sai Gon",
                AddressLabel.HOME,
                false
            )
        ).id();

        var updated = addresses.updateAddress(
            userId,
            id,
            new AddressRequest(
                "Tran Thi B",
                "0987654321",
                "Toa nha Keangnam, Phuong Me Tri, Ha Noi",
                AddressLabel.OFFICE,
                false
            )
        );

        assertThat(updated.recipientName()).isEqualTo("Tran Thi B");
        assertThat(updated.phoneNumber()).isEqualTo("0987654321");
        assertThat(updated.fullAddress()).isEqualTo("Toa nha Keangnam, Phuong Me Tri, Ha Noi");
        assertThat(updated.label()).isEqualTo(AddressLabel.OFFICE);
    }

    @Test
    void editsDefaultKeepsItDefault() {
        var userId = user();
        var id = addresses.addAddress(userId, address("Ha Noi")).id();

        var updated = addresses.updateAddress(userId, id, address("Hue"));

        assertThat(updated.fullAddress()).isEqualTo("Hue");
        assertThat(updated.defaultAddress()).isTrue();
    }

    @Test
    void updateCanMarkDefault() {
        var userId = user();
        addresses.addAddress(userId, address("Ha Noi"));
        var id = addresses.addAddress(userId, address("Hue")).id();

        var updated = addresses.updateAddress(
            userId,
            id,
            new AddressRequest("Nguyen Van A", "0912345678", "Hue", AddressLabel.HOME, true)
        );

        assertThat(updated.defaultAddress()).isTrue();

        var list = addresses.getAddresses(userId);
        assertThat(list.get(0).defaultAddress()).isFalse();
        assertThat(list.get(1).defaultAddress()).isTrue();
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
    void maxTenAddressesEnforced() {
        var userId = user();
        for (int i = 0; i < 10; i++) {
            addresses.addAddress(userId, address("City " + i));
        }

        assertThat(addresses.getAddresses(userId)).hasSize(10);

        assertThatThrownBy(() -> addresses.addAddress(userId, address("City 11")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> {
                assertStatus((ResponseStatusException) e, HttpStatus.BAD_REQUEST);
                assertThat(((ResponseStatusException) e).getReason()).contains("Maximum of 10 addresses reached");
            });
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
