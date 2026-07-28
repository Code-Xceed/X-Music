package com.codexceed.xmusic.hud;

import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.config.XMusicConfig;
import com.codexceed.xmusic.gui.render.ArtworkRenderer;
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
 * Clean, minimal HUD overlay for in-game playback status.
 * Design: Compact 82x70 overlay. Restored tracks always show thumbnails.
 */
public class MiniPlayerOverlay {

    // ── Layout ───────────────────────────────────────────────────────────
    private static final int HUD_W = 82;
    private static final int HUD_H = 70;
    private static final int MARGIN = 8;

    // ── Animation & Transition State ─────────────────────────────────────
    private float showProgress = 0f;
    private float glowPulse = 0f;

    private TrackRef renderedTrack = null;
    private int lastKnownLoopIteration = 0;
    private long lastKnownPosMs = 0;
    private boolean isTransitioningOut = false;

    public int getHudWidth() {
        return HUD_W;
    }
    public int getHudHeight() {
        return HUD_H;
    }

    private int resolveHudX(XMusicConfig cfg) {
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int hudW = getHudWidth();
        int x;
        if (cfg.hudX >= 0) {
            x = cfg.hudX;
        } else {
            switch (cfg.hudPosition) {
                case "TOP_LEFT": case "BOTTOM_LEFT": x = MARGIN; break;
                case "TOP_RIGHT": case "BOTTOM_RIGHT": x = screenW - hudW - MARGIN; break;
                default: x = (screenW - hudW) / 2; break;
            }
        }
        return Math.max(0, Math.min(screenW - hudW, x));
    }

    private int resolveHudY(XMusicConfig cfg) {
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int hudH = getHudHeight();
        int y;
        if (cfg.hudY >= 0) {
            y = cfg.hudY;
        } else {
            switch (cfg.hudPosition) {
                case "BOTTOM_LEFT": case "BOTTOM_RIGHT":
                    y = screenH - hudH - MARGIN;
                    break;
                default:
                    y = MARGIN;
                    break;
            }
        }
        return Math.max(0, Math.min(screenH - hudH, y));
    }

    // ── Main Render ──────────────────────────────────────────────────────

    public void render(GuiGraphics g, float partialTick) {
        XMusicConfig cfg = ConfigManager.get();
        if (!cfg.hudEnabled) return;

        PlayerState state = PlayerFacade.getInstance().snapshot();
        TrackRef track = state.getCurrentTrack();
        Font font = Minecraft.getInstance().font;
        int hudX = resolveHudX(cfg);
        int hudY = resolveHudY(cfg);

        // 1. Detect sound/track changes (looping, autoplay, restarts, next track)
        boolean trackChanged = false;
        if (track == null) {
            if (renderedTrack != null) {
                trackChanged = true;
            }
        } else {
            if (renderedTrack == null) {
                trackChanged = true;
            } else {
                // Check track ID
                if (!track.getId().equals(renderedTrack.getId())) {
                    trackChanged = true;
                }
                // Check loop iteration
                else if (state.getLoopIteration() != lastKnownLoopIteration) {
                    trackChanged = true;
                }
                // Check rewind/restart
                else if (state.getPositionMs() < lastKnownPosMs - 1500) {
                    trackChanged = true;
                }
            }
        }

        float delta = partialTick / 20f;

        // 2. Handle transition states
        if (trackChanged) {
            if (showProgress < 0.01f) {
                // If already invisible, swap instantly in the background
                renderedTrack = track;
                if (track != null) {
                    lastKnownLoopIteration = state.getLoopIteration();
                    lastKnownPosMs = state.getPositionMs();
                } else {
                    lastKnownLoopIteration = 0;
                    lastKnownPosMs = 0;
                }
                isTransitioningOut = false;
            } else if (!isTransitioningOut) {
                // Start transitioning out
                isTransitioningOut = true;
            }
        }

        // 3. Determine visibility (HUD only shows when track is not null and playing/paused)
        boolean shouldShow = (track != null) && (state.isPlaying() || state.isPaused()) && !isTransitioningOut;

        // 4. Approach transition progress
        float target = shouldShow ? 1f : 0f;
        showProgress = AnimationHelper.approach(showProgress, target, 8f, delta);

        // 5. Complete transition once fully hidden
        if (showProgress < 0.01f) {
            if (isTransitioningOut) {
                renderedTrack = track;
                if (track != null) {
                    lastKnownLoopIteration = state.getLoopIteration();
                    lastKnownPosMs = state.getPositionMs();
                } else {
                    lastKnownLoopIteration = 0;
                    lastKnownPosMs = 0;
                }
                isTransitioningOut = false;
            }
            return;
        }

        // 6. Keep tracking position/loop iteration under normal playback
        if (!isTransitioningOut && track != null) {
            lastKnownPosMs = state.getPositionMs();
            lastKnownLoopIteration = state.getLoopIteration();
        }

        // 7. Glow pulse
        if (state.isPlaying() && !isTransitioningOut) {
            glowPulse = AnimationHelper.approach(glowPulse, 1f, 3f, delta);
        } else {
            glowPulse = AnimationHelper.approach(glowPulse, 0f, 5f, delta);
        }

        // 8. Render compact HUD style
        if (showProgress > 0.001f) {
            renderCompactHud(g, font, hudX, hudY, state, renderedTrack, showProgress);
        }
    }

    public void renderCompactHud(GuiGraphics g, Font font, int x, int y, PlayerState state, TrackRef track, float alpha) {
        int w = HUD_W;
        int h = HUD_H;

        // Background gradient
        int frameTopColor = AnimationHelper.withAlpha(GuiTheme.FRAME_TOP, alpha);
        int frameBotColor = AnimationHelper.withAlpha(GuiTheme.FRAME_BOTTOM, alpha);
        GuiRender.gradientV(g, x, y, w, h, frameTopColor, frameBotColor);

        // Border (mcFrameBorder style)
        GuiRender.mcFrameBorder(g, x, y, w, h, alpha);

        // Animated multi-hue cycling outer glow
        long time = System.currentTimeMillis();
        float hue = (time % 5000) / 5000.0f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 0.8f, 0.9f);
        int glowColor = (int) (0x12 * alpha) << 24 | (rgb & 0xFFFFFF);
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, glowColor);

        // Thumbnail (16:9 ratio)
        int thumbX = x + 9;
        int thumbY = y + 8;
        int thumbW = 64;
        int thumbH = 36;
        GuiRender.mcWell(g, thumbX - 1, thumbY - 1, thumbW + 2, thumbH + 2, alpha);

        if (track != null) {
            ArtworkRenderer.renderArtwork(g, track, thumbX, thumbY, thumbW, thumbH, alpha);
        } else {
            ArtworkRenderer.renderPlaceholder(g, null, thumbX, thumbY, thumbW, thumbH, alpha);
        }

        // Pulsing thumbnail border
        float borderPulse = (track != null && state.isPlaying()) ? 0.6f + 0.4f * (float) Math.sin(time / 200.0) : 0.4f;
        int borderA = (int) (0xFF * borderPulse * alpha);
        int borderColor = (borderA << 24) | (GuiTheme.ACCENT & 0xFFFFFF);
        GuiRender.outline(g, thumbX - 1, thumbY - 1, thumbW + 2, thumbH + 2, borderColor);

        if (track != null && state.isPlaying()) {
            GuiRender.smoothHoverGlow(g, thumbX - 2, thumbY - 2, thumbW + 4, thumbH + 4, borderPulse * 0.8f * alpha);
        }

        // Track name (One line, centered, standard scale)
        String titleText = (track != null) ? track.getTitle() : "No Music";
        int maxW = w - 12; // 70px
        String truncatedTitle = font.plainSubstrByWidth(titleText, maxW);
        if (truncatedTitle.length() < titleText.length()) {
            truncatedTitle = font.plainSubstrByWidth(titleText, maxW - 8) + "...";
        }
        int titleW = font.width(truncatedTitle);
        int titleX = x + (w - titleW) / 2;
        int titleY = y + 50;
        int titleColor = (int) (0xFF * alpha) << 24 | ((track != null && state.isPlaying() ? GuiTheme.ACCENT : GuiTheme.TEXT) & 0x00FFFFFF);
        
        g.drawString(font, truncatedTitle, titleX, titleY, titleColor, true);

        // Small cozy waveform animation nestled at the bottom border
        int waveW = 50;
        int startX = x + (w - waveW) / 2;
        int waveY = y + h - 4; // rests just above the inner border bevel

        for (int i = 0; i < 13; i++) {
            int bx = startX + i * 4;
            int barH = 1;
            if (track != null && state.isPlaying()) {
                float bounce = (float) Math.sin((time * 0.015) + i * 0.7) * 0.5f + 0.5f;
                barH = 1 + (int) (bounce * 4f);
            } else if (track != null && state.isPaused()) {
                float bounce = (float) Math.sin((time * 0.003) + i * 0.4) * 0.2f + 0.3f;
                barH = 1 + (int) (bounce * 1.5f);
            }
            int barCol = (int) (0xB0 * alpha) << 24 | (GuiTheme.ACCENT & 0xFFFFFF);
            g.fill(bx, waveY - barH, bx + 2, waveY, barCol);
        }
    }
}
