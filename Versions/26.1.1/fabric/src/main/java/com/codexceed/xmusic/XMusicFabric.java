package com.codexceed.xmusic;

import com.codexceed.xmusic.audio.PlaybackMode;
import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.config.XMusicConfig;
import com.codexceed.xmusic.gui.screen.XMusicScreen;
import com.codexceed.xmusic.hud.HudRenderer;
import com.codexceed.xmusic.input.KeyBindings;
import com.codexceed.xmusic.player.PlayerFacade;
import com.codexceed.xmusic.platform.FabricPlatformHelper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;

/**
 * Fabric mod entry point.
 * Registers Fabric-specific event hooks and delegates to {@link XMusic}.
 */
public class XMusicFabric implements ClientModInitializer {

    private int tickCounter = 0;
    private boolean wasInWorld = false;

    private boolean restoredResumeState = false;

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

        // Register HUD render callback
        HudElementRegistry.addLast(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("xmusic", "hud"),
            (graphics, deltaTracker) -> HudRenderer.getInstance().render(graphics, deltaTracker.getRealtimeDeltaTicks())
        );
        XMusic.LOGGER.info("HUD render callback registered.");

        // Register client tick callback
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!restoredResumeState) {
                restoredResumeState = true;
                try {
                    PlayerFacade.getInstance().restoreResumeState();
                } catch (Exception e) {
                    XMusic.LOGGER.error("Failed to restore auto-resume state", e);
                }
            }

            // Tick the active playback backend.
            PlayerFacade.getInstance().tick();

            // Periodically save resume state (every ~5 seconds = 100 ticks)
            tickCounter++;
            if (tickCounter >= 100) {
                tickCounter = 0;
                PlayerFacade.getInstance().saveResumeState();
            }

            // Detect exiting a world to the main menu
            boolean inWorld = client.level != null;
            if (wasInWorld && !inWorld) {
                XMusicConfig cfg = ConfigManager.get();
                if (!cfg.playInMainMenu) {
                    PlayerFacade.getInstance().pause();
                    XMusic.LOGGER.info("[XMusic] Pausing playback on exit to main menu (playInMainMenu is false)");
                }
            }
            wasInWorld = inWorld;

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
                ((XMusicScreen) client.screen).closeAnimated();
            } else {
                client.setScreen(new XMusicScreen(client.screen));
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

        // Cycle loop mode (per-track: off → ×3 → ×5 → ∞ → off)
        while (KeyBindings.CYCLE_LOOP.consumeClick()) {
            PlayerFacade.getInstance().cycleLoopMode();
        }

        // Cycle playback mode (sequential → repeat_one → repeat_all → shuffle → sequential)
        while (KeyBindings.CYCLE_PLAYBACK_MODE.consumeClick()) {
            PlayerFacade.getInstance().cyclePlaybackMode();
        }
    }


}
