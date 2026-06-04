package com.evanp.f1.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot entry point for the F1 3D Race Visualizer.
 *
 * <p>scanBasePackages = "com.evanp.f1" ensures beans from all f1-* modules
 * (f1-core, f1-persistence, f1-ingestion, f1-ai) are discovered at startup.
 *
 * <p>EntityScan and EnableJpaRepositories are explicit because JPA entities
 * and repositories live in f1-persistence, outside this module's default
 * component scan root.
 */
@SpringBootApplication(scanBasePackages = "com.evanp.f1")
@EntityScan(basePackages = "com.evanp.f1")
@EnableJpaRepositories(basePackages = "com.evanp.f1")
@EnableScheduling
public class F1VisualizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(F1VisualizerApplication.class, args);
    }
}
