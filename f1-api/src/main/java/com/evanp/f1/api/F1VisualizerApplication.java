package com.evanp.f1.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.evanp.f1")
@EntityScan(basePackages = "com.evanp.f1")
@EnableJpaRepositories(basePackages = "com.evanp.f1")
@EnableScheduling
public class F1VisualizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(F1VisualizerApplication.class, args);
    }
}
