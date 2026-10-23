package com.minhnbnt.fashionstore.model;

import lombok.AccessLevel;
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

@Table("user_profile")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class UserProfile implements Persistable<UUID> {

    @Id
    private UUID id;

    private String email;

    private String displayName;

    private @Nullable String avatarKey;

    private UserRole role;

    private AccountStatus status;

    private @Nullable String statusReason;

    private Instant createdAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newRecord;

    public static UserProfile create(UUID id, String email, String displayName, @Nullable String avatarKey) {
        return new UserProfile(
            id, email, displayName, avatarKey, UserRole.CUSTOMER, AccountStatus.ACTIVE, null, Instant.now(), true);
    }

    public void rename(String displayName) {
        this.displayName = displayName;
    }

    public void changeEmail(String email) {
        this.email = email;
    }

    public void changeAvatar(@Nullable String avatarKey) {
        this.avatarKey = avatarKey;
    }

    /**
     * Chỉ nâng role (CUSTOMER → SHOP → ADMIN). Role thấp hơn hiện tại thì bỏ qua.
     */
    public void grantRole(UserRole granted) {
        if (granted.compareTo(role) > 0) {
            role = granted;
        }
    }

    public boolean isLocked() {
        return status == AccountStatus.LOCKED;
    }

    public void lock(String reason) {
        if (role == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Admin accounts cannot be locked");
        }
        if (status != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only active accounts can be locked");
        }
        status = AccountStatus.LOCKED;
        statusReason = reason;
    }

    public void unlock() {
        if (status != AccountStatus.LOCKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only locked accounts can be unlocked");
        }
        status = AccountStatus.ACTIVE;
        statusReason = null;
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
