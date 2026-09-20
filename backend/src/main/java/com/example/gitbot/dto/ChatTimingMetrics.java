package com.example.gitbot.dto;

import java.util.concurrent.TimeUnit;

public class ChatTimingMetrics {
    private final long requestStartNanos = System.nanoTime();
    private long prepDurationMs;
    private long ragTotalDurationMs;
    private long vectorSearchDurationMs;
    private long neighborDurationMs;
    private long promptBuildDurationMs;
    private long streamStartNanos;
    private long timeToFirstTokenMs = -1;
    private long streamTotalDurationMs;
    private long persistenceDurationMs;

    public void setPrepDurationMs(long prepDurationMs) {
        this.prepDurationMs = prepDurationMs;
    }

    public void setRagMetrics(long totalMs, long vectorSearchMs, long neighborMs) {
        this.ragTotalDurationMs = totalMs;
        this.vectorSearchDurationMs = vectorSearchMs;
        this.neighborDurationMs = neighborMs;
    }

    public void setPromptBuildDurationMs(long promptBuildDurationMs) {
        this.promptBuildDurationMs = promptBuildDurationMs;
    }

    public void markStreamStart() {
        this.streamStartNanos = System.nanoTime();
    }

    public void markFirstToken() {
        if (this.timeToFirstTokenMs < 0 && this.streamStartNanos > 0) {
            this.timeToFirstTokenMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - this.streamStartNanos);
        }
    }

    public void setStreamTotalDurationMs(long streamTotalDurationMs) {
        this.streamTotalDurationMs = streamTotalDurationMs;
    }

    public void setPersistenceDurationMs(long persistenceDurationMs) {
        this.persistenceDurationMs = persistenceDurationMs;
    }

    public long getStreamStartNanos() {
        return streamStartNanos;
    }

    public long getPrepDurationMs() {
        return prepDurationMs;
    }

    public long getRagTotalDurationMs() {
        return ragTotalDurationMs;
    }

    public long getVectorSearchDurationMs() {
        return vectorSearchDurationMs;
    }

    public long getNeighborDurationMs() {
        return neighborDurationMs;
    }

    public long getPromptBuildDurationMs() {
        return promptBuildDurationMs;
    }

    public long getTimeToFirstTokenMs() {
        return timeToFirstTokenMs;
    }

    public long getStreamTotalDurationMs() {
        return streamTotalDurationMs;
    }

    public long getPersistenceDurationMs() {
        return persistenceDurationMs;
    }

    public long getTotalLatencyMs() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestStartNanos);
    }
}
