package com.codexceed.xmusic.hud;

import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.config.XMusicConfig;
import com.codexceed.xmusic.gui.render.GuiRender;
import com.codexceed.xmusic.gui.theme.GuiTheme;
import com.codexceed.xmusic.gui.util.AnimationHelper;
import com.codexceed.xmusic.player.PlayerFacade;
import com.codexceed.xmusic.player.PlayerState;
import com.codexceed.xmusic.source.TrackRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Modern "Now Playing" toast notification.
 * Slides in from top-center with glow, accent bar, and smooth animations.
 * Auto-dismisses after 3.5 seconds.
 */
public class NowPlayingToast {

    private static final int TOAST_W = 220;
    private static final int TOAST_H = 38;
    private static final int MARGIN = 6;
    private static final int PAD = 10;
    private static final long DISPLAY_MS = 3500;
    private static final long ANIM_IN_MS = 400;
    private static final long ANIM_OUT_MS = 350;

    private String toastTrackId = "";
    private String toastTitle = "";
    private String toastArtist = "";
    private long showStartTime = 0;
    private boolean active = false;
    private String lastPlayingTrackId = "";

    public void render(GuiGraphics g, float partialTick) {
        XMusicConfig cfg = ConfigManager.get();
        if (!cfg.showNowPlayingToast) return;

        // Detect track change
        PlayerState state = PlayerFacade.getInstance().snapshot();
        TrackRef current = state.getCurrentTrack();
        String currentId = current != null ? current.getId() : "";

        if (!currentId.equals(lastPlayingTrackId) && !currentId.isEmpty() && state.isPlaying()) {
            lastPlayingTrackId = currentId;
            toastTrackId = currentId;
            toastTitle = current.getTitle();
            toastArtist = current.getArtist();
            showStartTime = System.currentTimeMillis();
            active = true;
        }

        if (!active || toastTrackId.isEmpty()) return;

        long elapsed = System.currentTimeMillis() - showStartTime;
        long totalDuration = ANIM_IN_MS + DISPLAY_MS + ANIM_OUT_MS;

        if (elapsed > totalDuration) {
            active = false;
            return;
        }

        float alpha;
        float slideY;
        float scaleEffect;

        if (elapsed < ANIM_IN_MS) {
            float t = AnimationHelper.easeOut((float) elapsed / ANIM_IN_MS);
            alpha = t;
            slideY = (1f - t) * -40f;
            scaleEffect = 0.92f + 0.08f * t; // slight scale-up on entry
        } else if (elapsed < ANIM_IN_MS + DISPLAY_MS) {
            alpha = 1f;
            slideY = 0f;
            scaleEffect = 1f;
        } else {
            float t = AnimationHelper.easeIn(
                    (float) (elapsed - ANIM_IN_MS - DISPLAY_MS) / ANIM_OUT_MS);
            alpha = 1f - t;
            slideY = t * -30f;
            scaleEffect = 1f - 0.05f * t; // slight scale-down on exit
        }

        Font font = Minecraft.getInstance().font;
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();

        // Center with slight scale offset
        int effectiveW = (int) (TOAST_W * scaleEffect);
        int x = (screenW - effectiveW) / 2;
        int y = MARGIN + (int) slideY;

        // ── Glow layers ───────────────────────────────────────────────────
        // Outer soft glow (3px)
        int glowAlpha3 = (int) (0x10 * alpha);
        int glowColor3 = (glowAlpha3 << 24) | (GuiTheme.ACCENT & 0x00FFFFFF);
        g.fill(x - 3, y - 3, x + effectiveW + 3, y - 1, glowColor3);
        g.fill(x - 3, y + TOAST_H + 1, x + effectiveW + 3, y + TOAST_H + 3, glowColor3);
        g.fill(x - 3, y - 1, x - 1, y + TOAST_H + 1, glowColor3);
        g.fill(x + effectiveW + 1, y - 1, x + effectiveW + 3, y + TOAST_H + 1, glowColor3);

        // Mid glow (1px)
        int glowAlpha1 = (int) (0x30 * alpha);
        int glowColor1 = (glowAlpha1 << 24) | (GuiTheme.ACCENT & 0x00FFFFFF);
        g.fill(x - 1, y - 1, x + effectiveW + 1, y, glowColor1);
        g.fill(x - 1, y + TOAST_H, x + effectiveW + 1, y + TOAST_H + 1, glowColor1);
        g.fill(x - 1, y, x, y + TOAST_H, glowColor1);
        g.fill(x + effectiveW, y, x + effectiveW + 1, y + TOAST_H, glowColor1);

        // ── Main panel ─────────────────────────────────────────────────────
        int bgAlpha = (int) (0xF0 * alpha);
        int bgColor = (bgAlpha << 24) | (GuiTheme.PANEL & 0x00FFFFFF);
        g.fill(x, y, x + effectiveW, y + TOAST_H, bgColor);

        // Top accent line (2px)
        int accentAlpha = (int) (0xFF * alpha);
        int accentBarColor = (accentAlpha << 24) | (GuiTheme.ACCENT & 0x00FFFFFF);
        g.fill(x, y, x + effectiveW, y + 2, accentBarColor);

        // Bevel edges
        int bevelAlpha = (int) (0xFF * alpha);
        int highlightColor = (bevelAlpha << 24) | (GuiTheme.BEVEL_HIGHLIGHT & 0x00FFFFFF);
        int shadowColor = (bevelAlpha << 24) | (GuiTheme.BEVEL_SHADOW & 0x00FFFFFF);
        g.fill(x, y + 2, x + 1, y + TOAST_H, highlightColor);
        g.fill(x + effectiveW - 1, y + 2, x + effectiveW, y + TOAST_H, shadowColor);
        g.fill(x, y + TOAST_H - 1, x + effectiveW, y + TOAST_H, shadowColor);

        // ── Content ────────────────────────────────────────────────────────
        // "NOW PLAYING" label (small caps style)
        int accentTextAlpha = (int) (0xFF * alpha);
        int accentTextColor = (accentTextAlpha << 24) | (GuiTheme.ACCENT & 0x00FFFFFF);
        g.drawString(font, "\u266A NOW PLAYING", x + PAD, y + 5, accentTextColor, true);

        // Track info line
        String info = toastTitle + " \u2014 " + toastArtist;
        int textAlpha = (int) (0xFF * alpha);
        int textColor = (textAlpha << 24) | (GuiTheme.TEXT_SOFT & 0x00FFFFFF);
        GuiRender.truncated(g, font, info, x + PAD, y + 17, effectiveW - PAD * 2, textColor);

        // Bottom progress shimmer line (decorative, shows toast is "alive")
        long shimmerPhase = (elapsed % 1500);
        float shimmerPos = shimmerPhase / 1500f;
        int shimmerW = 30;
        int shimmerStart = (int) ((effectiveW - shimmerW) * shimmerPos);
        int shimmerAlpha = (int) (0x40 * alpha);
        int shimmerColor = (shimmerAlpha << 24) | (GuiTheme.ACCENT & 0x00FFFFFF);
        g.fill(x + shimmerStart, y + TOAST_H - 2, x + shimmerStart + shimmerW, y + TOAST_H, shimmerColor);
    }
}
