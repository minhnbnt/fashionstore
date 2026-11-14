package com.minhnbnt.fashionstore.features.upload;

import com.minhnbnt.fashionstore.dto.upload.PresignRequest;
import com.minhnbnt.fashionstore.dto.upload.PresignResponse;
import com.minhnbnt.fashionstore.dto.upload.UploadPurpose;
import com.minhnbnt.fashionstore.features.upload.repository.UploadRepository;
import com.minhnbnt.fashionstore.infrastructure.S3Storage;
import com.minhnbnt.fashionstore.model.Upload;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Creates the upload record at presign time (design D2): the row exists before
 * the client PUTs the file, so business requests reference an uploadId instead
 * of an S3 key. Purpose decides the key prefix — {@code kyc/} files are denied
 * to anonymous reads by the bucket policy (design D3).
 */
@Service
@RequiredArgsConstructor
public class UploadService {

    private final S3Storage storage;
    private final UploadRepository uploads;

    public PresignResponse presign(PresignRequest request) {
        var signed = storage.presign(request.contentType(), prefixOf(request.purpose()));
        var saved = uploads.save(Upload.create(signed.key(), request.contentType()));

        return new PresignResponse(saved.getId(), signed.url(), signed.expiresAt());
    }

    private static String prefixOf(@Nullable UploadPurpose purpose) {
        if (purpose == UploadPurpose.KYC) {
            return "kyc/";
        }
        return "";
    }
}
