package com.codexceed.xmusic.gui.render;

import com.codexceed.xmusic.gui.theme.GuiTheme;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks hover enter/exit ticks per interactive region.
 * Provides a 0→1 lerp factor for smooth hover animations.
 */
public final class HoverTracker {
    private HoverTracker() {}

    private static final Map<String, Integer> TICKS = new HashMap<>();

    /** Call every render frame. Returns 0→1 lerp factor (0 = just entered, 1 = fully hovered). */
    public static float tick(String key, boolean hovered) {
        int t = TICKS.getOrDefault(key, 0);
        if (hovered) {
            t = Math.min(t + 1, GuiTheme.HOVER_TICKS);
        } else {
            t = Math.max(t - 1, 0);
        }
        TICKS.put(key, t);
        return (float) t / GuiTheme.HOVER_TICKS;
    }

    /** Get current lerp without advancing. */
    public static float current(String key) {
        return (float) TICKS.getOrDefault(key, 0) / GuiTheme.HOVER_TICKS;
    }

    /** Reset all trackers (e.g. on screen close). */
    public static void reset() {
        TICKS.clear();
    }
}
