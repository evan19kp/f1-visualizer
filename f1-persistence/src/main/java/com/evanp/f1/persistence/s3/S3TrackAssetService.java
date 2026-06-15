package com.evanp.f1.persistence.s3;

import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class S3TrackAssetService implements TrackAssetService {

    private static final Duration PRESIGN_TTL = Duration.ofMinutes(15);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties properties;

    public S3TrackAssetService(S3Client s3Client, S3Presigner s3Presigner, S3Properties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    @Override
    public Optional<URL> getPresignedTrackUrl(String circuitSlug) {
        if (circuitSlug == null || circuitSlug.isBlank()) {
            return Optional.empty();
        }

        String objectKey = "tracks/" + circuitSlug + ".glb";
        if (!objectExists(objectKey)) {
            return Optional.empty();
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(PRESIGN_TTL)
                .getObjectRequest(getObjectRequest)
                .build();

        return Optional.of(s3Presigner.presignGetObject(presignRequest).url());
    }

    @Override
    public void uploadTrackMesh(String circuitSlug, Path glbPath) {
        if (circuitSlug == null || circuitSlug.isBlank()) {
            throw new IllegalArgumentException("circuitSlug is required");
        }
        String objectKey = "tracks/" + circuitSlug + ".glb";
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(objectKey)
                        .build(),
                RequestBody.fromFile(glbPath));
    }

    private boolean objectExists(String objectKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }
}
