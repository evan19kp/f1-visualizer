/**
 * f1-ingestion: OpenF1 API polling, GPS-to-3D coordinate normalization,
 * and position publishing to Redis.
 *
 * <p>The scheduler polls OpenF1 every 1 second during a live Grand Prix session.
 * Coordinates are normalized to a [-1, 1] 3D space before being written to Redis.
 * This module never blocks the WebSocket data pipeline.
 */
package com.evanp.f1.ingestion;
