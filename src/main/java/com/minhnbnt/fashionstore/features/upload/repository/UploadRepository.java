package com.minhnbnt.fashionstore.features.upload.repository;

import com.minhnbnt.fashionstore.model.Upload;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface UploadRepository extends CrudRepository<Upload, UUID> {

    Optional<Upload> findByS3Key(String s3Key);
}
