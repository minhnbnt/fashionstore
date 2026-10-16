package com.minhnbnt.fashionstore.model;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserProfileTest {

    private static UserProfile customer() {
        return UserProfile.create(UUID.randomUUID(), "an@t.local", "An", null);
    }

    @Test
    void newProfileIsActiveCustomer() {
        var profile = customer();

        assertThat(profile.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(profile.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void lockStoresReason() {
        var profile = customer();

        profile.lock("Gian lận hoàn tiền");

        assertThat(profile.getStatus()).isEqualTo(AccountStatus.LOCKED);
        assertThat(profile.getStatusReason()).isEqualTo("Gian lận hoàn tiền");
    }

    @Test
    void lockTwiceFails() {
        var profile = customer();
        profile.lock("Spam");

        assertThatThrownBy(() -> profile.lock("Spam")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void adminCannotBeLocked() {
        var profile = customer();
        profile.grantRole(UserRole.ADMIN);

        assertThatThrownBy(() -> profile.lock("Spam")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void unlockClearsReason() {
        var profile = customer();
        profile.lock("Spam");

        profile.unlock();

        assertThat(profile.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(profile.getStatusReason()).isNull();
    }

    @Test
    void unlockActiveAccountFails() {
        var profile = customer();

        assertThatThrownBy(profile::unlock).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void grantRoleOnlyPromotes() {
        var profile = customer();
        profile.grantRole(UserRole.ADMIN);

        profile.grantRole(UserRole.SHOP);

        assertThat(profile.getRole()).isEqualTo(UserRole.ADMIN);
    }
}
