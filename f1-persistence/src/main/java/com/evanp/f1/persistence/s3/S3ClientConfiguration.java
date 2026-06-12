package com.evanp.f1.persistence.s3;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3ClientConfiguration {

    @Bean
    S3Client s3Client(S3Properties properties) {
        S3ClientBuilder builder =
                S3Client.builder().region(Region.of(properties.region()));
        applyEndpointOverride(builder, properties.endpoint());
        return builder.build();
    }

    @Bean
    S3Presigner s3Presigner(S3Properties properties) {
        S3Presigner.Builder builder =
                S3Presigner.builder().region(Region.of(properties.region()));
        String endpoint = properties.endpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    private static void applyEndpointOverride(S3ClientBuilder builder, String endpoint) {
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
        }
    }
}
