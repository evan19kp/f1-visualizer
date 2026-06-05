package com.evanp.f1.persistence.session;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RaceSessionRepository extends JpaRepository<RaceSessionEntity, Long> {}
