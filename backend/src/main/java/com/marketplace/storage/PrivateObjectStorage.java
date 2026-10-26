package com.marketplace.storage;

import com.marketplace.exception.ConflictException;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;
import java.time.Instant;

@Service
public class PrivateObjectStorage {

    public static final long MAX_OBJECT_BYTES = 10 * 1024 * 1024;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3StorageProperties properties;

    public PrivateObjectStorage(S3Client s3Client, S3Presigner s3Presigner, S3StorageProperties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    public PresignedUpload presignPut(String objectKey, String contentType, long contentLength) {
        if (contentLength < 1 || contentLength > MAX_OBJECT_BYTES) {
            throw new ConflictException("Private object must be between 1 byte and 10 MB.");
        }
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(10));
        PutObjectRequest putObject = PutObjectRequest.builder()
                .bucket(properties.bucketName())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(10))
                        .putObjectRequest(putObject)
                        .build());
        return new PresignedUpload(presigned.url().toString(), expiresAt);
    }

    public boolean existsWithinLimit(String objectKey) {
        try {
            long size = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucketName())
                    .key(objectKey)
                    .build()).contentLength();
            return size > 0 && size <= MAX_OBJECT_BYTES;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw exception;
        }
    }

    public String presignGet(String objectKey) {
        return presignGet(objectKey, true);
    }

    public String presignGet(String objectKey, boolean attachment) {
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(15))
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(properties.bucketName())
                                .key(objectKey)
                                .responseContentDisposition(attachment ? "attachment" : "inline")
                                .build())
                        .build());
        return presigned.url().toString();
    }

    public record PresignedUpload(String uploadUrl, Instant expiresAt) {
    }
}
