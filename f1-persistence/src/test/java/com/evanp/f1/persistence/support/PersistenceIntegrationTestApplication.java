package com.evanp.f1.persistence.support;

import com.evanp.f1.persistence.session.RaceSessionEntity;
import com.evanp.f1.persistence.session.RaceSessionRepository;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = RaceSessionEntity.class)
@EnableJpaRepositories(basePackageClasses = RaceSessionRepository.class)
public class PersistenceIntegrationTestApplication {}
