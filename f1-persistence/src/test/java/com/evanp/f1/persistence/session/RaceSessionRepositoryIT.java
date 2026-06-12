package com.evanp.f1.persistence.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.evanp.f1.persistence.support.AbstractContainersIT;
import com.evanp.f1.persistence.support.PersistenceIntegrationTestApplication;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = PersistenceIntegrationTestApplication.class)
@ActiveProfiles("it")
@Transactional
class RaceSessionRepositoryIT extends AbstractContainersIT {

    @Autowired
    private RaceSessionRepository repository;

    @Test
    void saveAndFindById_persistsThroughFlywaySchema() {
        Instant now = Instant.parse("2024-03-02T15:00:00Z");
        RaceSessionEntity entity = new RaceSessionEntity();
        entity.setSessionKey(9161L);
        entity.setMeetingKey(1229L);
        entity.setSessionName("Race");
        entity.setCircuitName("Bahrain");
        entity.setDateStart(now);
        entity.setCreatedAt(now);

        repository.saveAndFlush(entity);

        assertThat(repository.findById(9161L))
                .isPresent()
                .get()
                .satisfies(found -> {
                    assertThat(found.getSessionName()).isEqualTo("Race");
                    assertThat(found.getCircuitName()).isEqualTo("Bahrain");
                    assertThat(found.getMeetingKey()).isEqualTo(1229L);
                });
    }
}
