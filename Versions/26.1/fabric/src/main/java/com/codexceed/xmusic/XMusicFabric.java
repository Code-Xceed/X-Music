package com.codexceed.xmusic;

import com.codexceed.xmusic.audio.PlaybackMode;
import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.gui.screen.XMusicScreen;
import com.codexceed.xmusic.hud.HudRenderer;
import com.codexceed.xmusic.input.KeyBindings;
import com.codexceed.xmusic.player.PlayerFacade;
import com.codexceed.xmusic.platform.FabricPlatformHelper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Fabric mod entry point.
 * Registers Fabric-specific event hooks and delegates to {@link XMusic}.
 */
public class XMusicFabric implements ClientModInitializer {

    private int tickCounter = 0;

    // Common init is done inside onInitializeClient to avoid loading
    // client-only classes (Screen) during the 'main' entrypoint stage.

    @Override
    public void onInitializeClient() {
        XMusic.LOGGER.info("Fabric client initialization starting.");

        // Common initialization (moved from onInitialize to avoid main-stage classloading)
        XMusic.init(new FabricPlatformHelper());

        // Register key bindings
        for (KeyMapping key : KeyBindings.getAll()) {
            KeyMappingHelper.registerKeyMapping(key);
        }
        XMusic.LOGGER.info("Key bindings registered.");

        // Client-side initialization (audio engine, services, etc.)
        XMusic.initClient();
        XMusic.LOGGER.info("Fabric client services initialized.");

        // Auto-resume last playing track
        PlayerFacade.getInstance().restoreResumeState();

        // Register HUD element
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("xmusic", "hud"),
            (graphics, deltaTracker) -> {
                HudRenderer.getInstance().render(graphics, deltaTracker.getRealtimeDeltaTicks());
            }
        );
        XMusic.LOGGER.info("HUD render callback registered.");

        // Register client tick callback
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Tick the active playback backend.
            PlayerFacade.getInstance().tick();

            // Periodically save resume state (every ~5 seconds = 100 ticks)
            tickCounter++;
            if (tickCounter >= 100) {
                tickCounter = 0;
                PlayerFacade.getInstance().saveResumeState();
            }

            // â”€â”€ Playback Context Enforcement â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            enforcePlaybackContext(client);

            // Handle key bindings
            handleKeyBindings(client);
        });
        XMusic.LOGGER.info("Client tick callback registered.");

        // Save resume state when client is closing
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            PlayerFacade.getInstance().saveResumeState();
        }));
    }

    private void handleKeyBindings(Minecraft client) {
        while (KeyBindings.OPEN_PLAYER.consumeClick()) {
            if (client.screen instanceof XMusicScreen) {
                client.setScreen(null);
            } else if (client.screen == null) {
                client.setScreen(new XMusicScreen());
            }
        }

        // Play/Pause
        while (KeyBindings.PLAY_PAUSE.consumeClick()) {
            PlayerFacade.getInstance().togglePlayPause();
        }

        // Next track
        while (KeyBindings.NEXT_TRACK.consumeClick()) {
            PlayerFacade.getInstance().next();
        }

        // Previous track
        while (KeyBindings.PREV_TRACK.consumeClick()) {
            PlayerFacade.getInstance().previous();
        }

        // Volume up
        while (KeyBindings.VOLUME_UP.consumeClick()) {
            PlayerFacade.getInstance().adjustVolume(ConfigManager.get().volumeStep);
        }

        // Volume down
        while (KeyBindings.VOLUME_DOWN.consumeClick()) {
            PlayerFacade.getInstance().adjustVolume(-ConfigManager.get().volumeStep);
        }

        // Toggle shuffle
        while (KeyBindings.TOGGLE_SHUFFLE.consumeClick()) {
            PlayerFacade facade = PlayerFacade.getInstance();
            if (facade.getPlaybackMode() == PlaybackMode.SHUFFLE) {
                facade.setPlaybackMode(PlaybackMode.SEQUENTIAL);
            } else {
                facade.setPlaybackMode(PlaybackMode.SHUFFLE);
            }
        }

        // Cycle loop mode (per-track: off â†’ Ã—3 â†’ Ã—5 â†’ âˆž â†’ off)
        while (KeyBindings.CYCLE_LOOP.consumeClick()) {
            PlayerFacade.getInstance().cycleLoopMode();
        }

        // Cycle playback mode (sequential â†’ repeat_one â†’ repeat_all â†’ shuffle â†’ sequential)
        while (KeyBindings.CYCLE_PLAYBACK_MODE.consumeClick()) {
            PlayerFacade.getInstance().cyclePlaybackMode();
        }
    }

    // â”€â”€ Playback Context â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** True when playback was paused by context enforcement (not by user). */
    private boolean contextPaused = false;

    /**
     * Enforce the playbackContext setting.
     * IN_WORLD: pause when not in a world (title screen, menus).
     * MAIN_MENU: pause when in a world.
     * EVERYWHERE: no restrictions.
     */
    private void enforcePlaybackContext(Minecraft client) {
        String context = ConfigManager.get().playbackContext;
        if ("EVERYWHERE".equals(context)) {
            // If we previously context-paused, resume
            if (contextPaused) {
                contextPaused = false;
                PlayerFacade.getInstance().resume();
            }
            return;
        }

        boolean inWorld = client.level != null && client.player != null;
        boolean shouldPause;

        if ("IN_WORLD".equals(context)) {
            // Music only in-world â†’ pause when NOT in world
            shouldPause = !inWorld;
        } else if ("MAIN_MENU".equals(context)) {
            // Music only on main menu â†’ pause when IN world
            shouldPause = inWorld;
        } else {
            return; // unknown, do nothing
        }

        PlayerFacade player = PlayerFacade.getInstance();
        if (shouldPause && player.snapshot().isPlaying()) {
            player.pause();
            contextPaused = true;
        } else if (!shouldPause && contextPaused) {
            contextPaused = false;
            player.resume();
        }
    }
}
