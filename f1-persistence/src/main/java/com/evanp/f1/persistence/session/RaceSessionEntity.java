package com.evanp.f1.persistence.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "race_sessions")
public class RaceSessionEntity {

    @Id
    @Column(name = "session_key")
    private Long sessionKey;

    @Column(name = "meeting_key", nullable = false)
    private Long meetingKey;

    @Column(name = "session_name", length = 100)
    private String sessionName;

    @Column(name = "circuit_name", length = 100)
    private String circuitName;

    @Column(name = "date_start")
    private Instant dateStart;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RaceSessionEntity() {}

    public Long getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(Long sessionKey) {
        this.sessionKey = sessionKey;
    }

    public Long getMeetingKey() {
        return meetingKey;
    }

    public void setMeetingKey(Long meetingKey) {
        this.meetingKey = meetingKey;
    }

    public String getSessionName() {
        return sessionName;
    }

    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }

    public String getCircuitName() {
        return circuitName;
    }

    public void setCircuitName(String circuitName) {
        this.circuitName = circuitName;
    }

    public Instant getDateStart() {
        return dateStart;
    }

    public void setDateStart(Instant dateStart) {
        this.dateStart = dateStart;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
