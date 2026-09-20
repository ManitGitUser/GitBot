package com.example.gitbot.dto;

public final class ChatTimingContext {
    private static final ThreadLocal<ChatTimingMetrics> CURRENT = new ThreadLocal<>();

    private ChatTimingContext() {}

    public static void set(ChatTimingMetrics metrics) {
        CURRENT.set(metrics);
    }

    public static ChatTimingMetrics get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
