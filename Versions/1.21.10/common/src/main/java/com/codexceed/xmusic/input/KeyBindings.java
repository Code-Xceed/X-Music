package com.codexceed.xmusic.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * All key bindings for CodeX Music Player.
 * Registered by the loader-specific platform helpers.
 */
public final class KeyBindings {
    public static final String CATEGORY = "key.categories.xmusic";

    /** Open the music player GUI. */
    public static final KeyMapping OPEN_PLAYER = new KeyMapping(
            "key.xmusic.open_player",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            CATEGORY
    );

    /** Toggle play/pause */
    public static final KeyMapping PLAY_PAUSE = new KeyMapping(
            "key.xmusic.play_pause",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            CATEGORY
    );

    /** Next track */
    public static final KeyMapping NEXT_TRACK = new KeyMapping(
            "key.xmusic.next_track",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            CATEGORY
    );

    /** Previous track */
    public static final KeyMapping PREV_TRACK = new KeyMapping(
            "key.xmusic.prev_track",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_BRACKET,
            CATEGORY
    );

    /** Volume up */
    public static final KeyMapping VOLUME_UP = new KeyMapping(
            "key.xmusic.volume_up",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PERIOD,
            CATEGORY
    );

    /** Volume down */
    public static final KeyMapping VOLUME_DOWN = new KeyMapping(
            "key.xmusic.volume_down",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_COMMA,
            CATEGORY
    );

    /** Toggle shuffle */
    public static final KeyMapping TOGGLE_SHUFFLE = new KeyMapping(
            "key.xmusic.toggle_shuffle",
            InputConstants.Type.KEYSYM,
            -1,
            CATEGORY
    );

    /** Cycle loop mode */
    public static final KeyMapping CYCLE_LOOP = new KeyMapping(
            "key.xmusic.cycle_loop",
            InputConstants.Type.KEYSYM,
            -1,
            CATEGORY
    );

    /** Cycle playback mode */
    public static final KeyMapping CYCLE_PLAYBACK_MODE = new KeyMapping(
            "key.xmusic.cycle_playback_mode",
            InputConstants.Type.KEYSYM,
            -1,
            CATEGORY
    );

    private KeyBindings() {}

    /**
     * Returns all key bindings for registration.
     */
    public static KeyMapping[] getAll() {
        return new KeyMapping[]{
                OPEN_PLAYER,
                PLAY_PAUSE,
                NEXT_TRACK,
                PREV_TRACK,
                VOLUME_UP,
                VOLUME_DOWN,
                TOGGLE_SHUFFLE,
                CYCLE_LOOP,
                CYCLE_PLAYBACK_MODE
        };
    }
}
