package com.evanp.f1.persistence.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3TrackAssetServiceTest {

    private static final String BUCKET = "f1-visualizer-assets";

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3TrackAssetService service;

    @BeforeEach
    void setUp() {
        service = new S3TrackAssetService(
                s3Client, s3Presigner, new S3Properties(BUCKET, "us-east-1", null));
    }

    @Test
    void getPresignedTrackUrl_returnsUrlWhenObjectExists() throws MalformedURLException {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

        URL expectedUrl = new URL("https://example.com/tracks/bahrain.glb?sig=abc");
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(expectedUrl);
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        Optional<URL> result = service.getPresignedTrackUrl("bahrain");

        assertThat(result).contains(expectedUrl);
    }

    @Test
    void getPresignedTrackUrl_returnsEmptyWhenObjectMissing() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("not found").build());

        Optional<URL> result = service.getPresignedTrackUrl("bahrain");

        assertThat(result).isEmpty();
        verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void getPresignedTrackUrl_returnsEmptyForBlankSlug() {
        assertThat(service.getPresignedTrackUrl(null)).isEmpty();
        assertThat(service.getPresignedTrackUrl("")).isEmpty();
        assertThat(service.getPresignedTrackUrl("   ")).isEmpty();
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }
}
