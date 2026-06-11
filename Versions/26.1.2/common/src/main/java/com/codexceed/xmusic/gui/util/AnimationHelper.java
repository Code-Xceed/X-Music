package com.codexceed.xmusic.gui.util;

/**
 * Easing functions and animation utilities for smooth UI transitions.
 * Provides time-based progress helpers, stagger offsets, and color interpolation
 * for premium intro/outro and hover animations across the entire GUI.
 */
public final class AnimationHelper {

    private AnimationHelper() {}

    // â”€â”€ Easing Curves â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

    /** Quart ease-out for snappier deceleration. */
    public static float easeOutQuart(float t) {
        t = clamp(t);
        float inv = 1f - t;
        return 1f - inv * inv * inv * inv;
    }

    /** Quint ease-out for ultra-smooth deceleration. */
    public static float easeOutQuint(float t) {
        t = clamp(t);
        float inv = 1f - t;
        return 1f - inv * inv * inv * inv * inv;
    }

    /** Expo ease-out â€” aggressive start, gentle finish. */
    public static float easeOutExpo(float t) {
        t = clamp(t);
        return t >= 1f ? 1f : 1f - (float) Math.pow(2, -10 * t);
    }

    /** Back ease-out â€” slight overshoot for spring-like feel. */
    public static float easeOutBack(float t) {
        t = clamp(t);
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    public static float spring(float t) {
        t = clamp(t);
        return (float) (1.0 + Math.pow(2, -10 * t) * Math.sin((t - 0.075) * (2 * Math.PI) / 0.3));
    }

    // â”€â”€ Smooth Approach (delta-time lerp) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Delta-time based smooth approach (for continuous animations).
     * Call this every frame: value lerps toward target.
     *
     * @param current Current value
     * @param target  Target value
     * @param speed   Approach speed (higher = faster, e.g. 8â€“15)
     * @param delta   Delta time in seconds (partialTick / 20.0)
     */
    public static float approach(float current, float target, float speed, float delta) {
        float diff = target - current;
        if (Math.abs(diff) < 0.001f) return target;
        return current + diff * (1f - (float) Math.exp(-speed * delta));
    }

    // â”€â”€ Time-Based Progress â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

    // â”€â”€ Staggered Cascade Animations â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Returns a staggered progress value for a child element in a cascade.
     * Each child starts its animation slightly after the previous one.
     *
     * @param parentProgress Overall parent progress 0â†’1
     * @param index          Child index (0-based)
     * @param totalChildren  Total number of children in the cascade
     * @param overlap        How much animations overlap (0.0 = none, 1.0 = full)
     * @return The individual child's progress 0â†’1
     */
    public static float stagger(float parentProgress, int index, int totalChildren, float overlap) {
        if (totalChildren <= 1) return clamp(parentProgress);
        float windowSize = 1f / (1f + (totalChildren - 1) * (1f - overlap));
        float start = index * windowSize * (1f - overlap);
        float end = start + windowSize;
        float t = (parentProgress - start) / (end - start);
        return clamp(t);
    }

    // â”€â”€ Color Interpolation â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Interpolates between two ARGB colors by factor t (0â†’1).
     * Blends each channel independently for smooth color transitions.
     */
    public static int lerpColor(int from, int to, float t) {
        t = clamp(t);
        int aF = (from >> 24) & 0xFF, rF = (from >> 16) & 0xFF, gF = (from >> 8) & 0xFF, bF = from & 0xFF;
        int aT = (to >> 24) & 0xFF, rT = (to >> 16) & 0xFF, gT = (to >> 8) & 0xFF, bT = to & 0xFF;
        int a = aF + (int) ((aT - aF) * t);
        int r = rF + (int) ((rT - rF) * t);
        int g = gF + (int) ((gT - gF) * t);
        int b = bF + (int) ((bT - bF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Applies an alpha multiplier to an existing ARGB color.
     */
    public static int withAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * clamp(alpha));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /**
     * Creates a pulsing alpha value for glow effects.
     * Returns 0â†’1 sine-based pulse at the given frequency.
     */
    public static float pulse(float frequencyHz) {
        double t = System.currentTimeMillis() / 1000.0;
        return (float) (Math.sin(t * Math.PI * 2 * frequencyHz) * 0.5 + 0.5);
    }

    // â”€â”€ Linear Interpolation â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp(t);
    }

    public static int lerp(int a, int b, float t) {
        return a + (int) ((b - a) * clamp(t));
    }

    // â”€â”€ Utility â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Public clamp to 0â€“1 range. */
    public static float clamp01(float t) {
        return Math.max(0f, Math.min(1f, t));
    }

    private static float clamp(float t) {
        return Math.max(0f, Math.min(1f, t));
    }
}
