package com.codexceed.xmusic.gui.util;

/**
 * Easing functions and animation utilities for smooth UI transitions.
 */
public final class AnimationHelper {

    private AnimationHelper() {}

    public static float easeInOut(float t) {
        t = clamp(t);
        return t < 0.5f
                ? 4f * t * t * t
                : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    public static float easeOut(float t) {
        t = clamp(t);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    public static float easeIn(float t) {
        t = clamp(t);
        return t * t * t;
    }

    public static float spring(float t) {
        t = clamp(t);
        return (float) (1.0 + Math.pow(2, -10 * t) * Math.sin((t - 0.075) * (2 * Math.PI) / 0.3));
    }

    /**
     * Delta-time based smooth approach (for continuous animations).
     * Call this every frame: value lerps toward target.
     *
     * @param current Current value
     * @param target  Target value
     * @param speed   Approach speed (higher = faster, e.g. 8–15)
     * @param delta   Delta time in seconds (partialTick / 20.0)
     */
    public static float approach(float current, float target, float speed, float delta) {
        float diff = target - current;
        if (Math.abs(diff) < 0.001f) return target;
        return current + diff * (1f - (float) Math.exp(-speed * delta));
    }

    /**
     * Calculate animation progress given start time and duration.
     */
    public static float progress(long startTimeMs, long durationMs) {
        if (durationMs <= 0) return 1f;
        float t = (System.currentTimeMillis() - startTimeMs) / (float) durationMs;
        return clamp(t);
    }

    public static boolean isFinished(long startTimeMs, long durationMs) {
        return System.currentTimeMillis() - startTimeMs >= durationMs;
    }

    private static float clamp(float t) {
        return Math.max(0f, Math.min(1f, t));
    }
}
