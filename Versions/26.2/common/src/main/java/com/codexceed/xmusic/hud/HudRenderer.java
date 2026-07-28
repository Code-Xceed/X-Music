package com.codexceed.xmusic.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Master HUD renderer. Delegates to the mini-player overlay.
 * Called from the Fabric HudRenderCallback.
 */
public class HudRenderer {

    private static HudRenderer instance;

    private final MiniPlayerOverlay miniPlayer;

    private HudRenderer() {
        miniPlayer = new MiniPlayerOverlay();
    }

    public static HudRenderer getInstance() {
        if (instance == null) {
            instance = new HudRenderer();
        }
        return instance;
    }

    /**
     * Render all HUD overlays. Called from the platform HUD render event.
     * Only renders when no screen is open (in-game HUD).
     */
    public void render(GuiGraphicsExtractor g, float partialTick) {
        // Only render in-game (no screen open)
        if (Minecraft.getInstance().gui.screen() != null) return;

        miniPlayer.render(g, partialTick);
    }

    public MiniPlayerOverlay getMiniPlayer() {
        return miniPlayer;
    }
}
