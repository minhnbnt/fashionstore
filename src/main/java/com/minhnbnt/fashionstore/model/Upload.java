package com.minhnbnt.fashionstore.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("upload")
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
public class Upload implements Persistable<UUID> {

    @Id
    private UUID id;

    private String s3Key;

    private String contentType;

    private Instant createdAt;

    @Transient
    private boolean newRecord;

    public static Upload create(String s3Key, String contentType) {
        return new Upload(UUID.randomUUID(), s3Key, contentType, Instant.now(), true);
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
