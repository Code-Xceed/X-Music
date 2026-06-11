package com.codexceed.xmusic.gui.render;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks hover enter/exit state per interactive region using smooth
 * delta-time interpolation (not tick-based) for buttery-smooth hover
 * transitions at any frame rate.
 * 
 * Each region is identified by a string key. The tracker stores a float
 * 0â†’1 value that smoothly ramps up when hovered and ramps down when not.
 */
public final class HoverTracker {
    private HoverTracker() {}

    /** Speed of hover-in transition (higher = faster). */
    private static final float HOVER_IN_SPEED = 12f;
    /** Speed of hover-out transition (higher = faster). */
    private static final float HOVER_OUT_SPEED = 8f;

    private static final Map<String, Float> VALUES = new HashMap<>();
    private static long lastFrameMs = System.currentTimeMillis();
    private static float frameDelta = 0f;

    /**
     * Must be called ONCE at the start of each render frame to update
     * the global delta time. Called from XMusicScreen.render().
     */
    public static void updateFrameDelta() {
        long now = System.currentTimeMillis();
        frameDelta = Math.min((now - lastFrameMs) / 1000f, 0.05f); // cap at 50ms
        lastFrameMs = now;
    }

    /**
     * Smoothly animate a hover value for the given key.
     * Call every frame for each interactive region.
     *
     * @param key     Unique identifier for this region
     * @param hovered Whether the mouse is currently over this region
     * @return Current hover factor 0â†’1 (smoothly interpolated)
     */
    public static float tick(String key, boolean hovered) {
        float current = VALUES.getOrDefault(key, 0f);
        float target = hovered ? 1f : 0f;
        float speed = hovered ? HOVER_IN_SPEED : HOVER_OUT_SPEED;

        float diff = target - current;
        if (Math.abs(diff) < 0.002f) {
            current = target;
        } else {
            current += diff * (1f - (float) Math.exp(-speed * frameDelta));
        }

        VALUES.put(key, current);
        return current;
    }

    /** Get current lerp without advancing. */
    public static float current(String key) {
        return VALUES.getOrDefault(key, 0f);
    }

    /** Reset all trackers (e.g. on screen close). */
    public static void reset() {
        VALUES.clear();
        lastFrameMs = System.currentTimeMillis();
    }
}
