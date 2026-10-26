package com.marketplace.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage.s3")
public record S3StorageProperties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucketName
) {
}
