# Sprint 11 — Agent B: Ingestion status

## Branch strategy

| Field | Value |
|-------|-------|
| **Mode** | Own branch |
| **Branch** | `sprint11/ingestion-status` |
| **Base** | `main` after A |
| **PR target** | `main` |
| **Merge order** | 2nd parallel |

Default `OPENF1_SESSION_KEY=9161`, `IngestionStatusService` + `GET /api/ingestion/status`, fix `SessionResetService` for `latest`. Frontend SessionPicker highlight and EmptyTrackOverlay diagnostics are owned by Agent D (consumes this API).
