package com.minhnbnt.fashionstore.model;

import lombok.AccessLevel;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("user_profile")
@Getter
public class UserProfile implements Persistable<UUID> {

    @Id
    private UUID id;

    private String email;

    private String displayName;

    private @Nullable String avatarKey;

    private Instant createdAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newRecord;

    @PersistenceCreator
    public UserProfile(
        UUID id,
        String email,
        String displayName,
        @Nullable String avatarKey,
        Instant createdAt
    ) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.avatarKey = avatarKey;
        this.createdAt = createdAt;
        this.newRecord = false;
    }

    public static UserProfile create(UUID id, String email, String displayName, @Nullable String avatarKey) {
        var profile = new UserProfile(id, email, displayName, avatarKey, Instant.now());
        profile.newRecord = true;
        return profile;
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

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newRecord;
    }
}
