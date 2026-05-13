package com.codexceed.xmusic.hud;

import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.config.XMusicConfig;
import com.codexceed.xmusic.gui.render.GuiRender;
import com.codexceed.xmusic.gui.theme.GuiTheme;
import com.codexceed.xmusic.gui.util.AnimationHelper;
import com.codexceed.xmusic.player.PlayerFacade;
import com.codexceed.xmusic.player.PlayerState;
import com.codexceed.xmusic.source.PlaybackType;
import com.codexceed.xmusic.source.TrackRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Modern in-game mini-player HUD overlay.
 * Rounded corners, waveform visualizer, glow, unified slide/fade
 * animation for panel + all content. Auto-shows on any music state change.
 * Shows source label (YouTube, Spotify, Local, etc.) for all track types.
 */
public class MiniPlayerOverlay {

    private static final int HUD_W = 190;
    private static final int HUD_H = 34;
    private static final int MARGIN = 10;
    private static final int PAD = 8;
    private static final int RADIUS = 5;
    private static final int WAVE_BARS = 14;
    private static final int WAVE_BAR_W = 2;
    private static final int WAVE_BAR_GAP = 1;
    private static final int WAVE_MAX_H = 18;
    private static final int WAVE_MIN_H = 2;
    private static final int PROGRESS_H = 2;

    private float showProgress = 0f;
    private long lastActivityTime = 0;
    private String lastStateKey = "";
    private float glowPulse = 0f;
    private final float[] waveHeights = new float[WAVE_BARS];
    private final float[] waveTargets = new float[WAVE_BARS];
    private long lastWaveUpdate = 0;
    private long lastKnownPosMs = 0;
    private boolean draggingProgress = false;

    public int getHudWidth() { return HUD_W; }
    public int getHudHeight() { return HUD_H; }
    public boolean isDraggingProgress() { return draggingProgress; }

    /** Handle mouse click for seeking on the progress line. */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        XMusicConfig cfg = ConfigManager.get();
        if (!cfg.hudEnabled || showProgress < 0.5f) return false;

        PlayerState state = PlayerFacade.getInstance().snapshot();
        TrackRef track = state.getCurrentTrack();
        if (track == null || track.getPlaybackType() == PlaybackType.NATIVE) return false;
        if (state.getDurationMs() <= 0) return false;

        // Check if click is on the progress bar area (bottom 6px of HUD)
        int hudX = resolveHudX(cfg);
        int hudY = resolveHudY(cfg);
        int progY = hudY + HUD_H - PROGRESS_H - 2;

        if (mouseX >= hudX && mouseX <= hudX + HUD_W && mouseY >= progY - 2 && mouseY <= hudY + HUD_H) {
            draggingProgress = true;
            seekFromMouse(mouseX, hudX, state);
            return true;
        }
        return false;
    }

    /** Handle mouse drag for seeking. */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!draggingProgress) return false;
        XMusicConfig cfg = ConfigManager.get();
        int hudX = resolveHudX(cfg);
        PlayerState state = PlayerFacade.getInstance().snapshot();
        seekFromMouse(mouseX, hudX, state);
        return true;
    }

    /** Handle mouse release. */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingProgress) {
            draggingProgress = false;
            return true;
        }
        return false;
    }

    private void seekFromMouse(double mouseX, int hudX, PlayerState state) {
        float pct = (float) Math.max(0, Math.min(1, (mouseX - hudX) / HUD_W));
        long seekMs = (long) (pct * state.getDurationMs());
        PlayerFacade.getInstance().seek(seekMs);
    }

    private int resolveHudX(XMusicConfig cfg) {
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        if (cfg.hudX >= 0) return cfg.hudX;
        switch (cfg.hudPosition) {
            case "TOP_LEFT": case "BOTTOM_LEFT": return MARGIN;
            case "TOP_RIGHT": case "BOTTOM_RIGHT": return screenW - HUD_W - MARGIN;
            default: return (screenW - HUD_W) / 2;
        }
    }

    private int resolveHudY(XMusicConfig cfg) {
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        if (cfg.hudY >= 0) return cfg.hudY;
        switch (cfg.hudPosition) {
            case "BOTTOM_LEFT": case "BOTTOM_RIGHT": return screenH - HUD_H - MARGIN;
            default: return MARGIN;
        }
    }

    public void render(GuiGraphics g, float partialTick) {
        XMusicConfig cfg = ConfigManager.get();
        if (!cfg.hudEnabled) return;

        PlayerState state = PlayerFacade.getInstance().snapshot();
        TrackRef track = state.getCurrentTrack();

        boolean isActive = state.isPlaying() || state.isPaused();

        // Detect track changes and loop restarts (NOT play/pause toggles)
        String stateKey = buildStateKey(state, track);
        if (!stateKey.equals(lastStateKey)) {
            lastStateKey = stateKey;
            lastActivityTime = System.currentTimeMillis();
            // Only reset animation for INTRO (new track/loop), not for fade-out
            if (isActive) {
                showProgress = 0f;
            }
        }

        // Auto-hide logic
        boolean shouldShow = isActive;
        int autoHide = cfg.hudAutoHideSeconds;
        if (autoHide > 0 && isActive) {
            long elapsed = System.currentTimeMillis() - lastActivityTime;
            if (elapsed > autoHide * 1000L) {
                shouldShow = false;
            }
        }

        // Animate show/hide
        float target = shouldShow ? 1f : 0f;
        float delta = partialTick / 20f;
        showProgress = AnimationHelper.approach(showProgress, target, 6f, delta);
        if (showProgress < 0.005f) return;

        // Track position for loop detection
        if (track != null) {
            lastKnownPosMs = state.getPositionMs();
        }

        // Glow pulse when playing
        if (state.isPlaying()) {
            glowPulse = AnimationHelper.approach(glowPulse, 1f, 3f, delta);
        } else {
            glowPulse = AnimationHelper.approach(glowPulse, 0f, 5f, delta);
        }

        Font font = Minecraft.getInstance().font;
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        // Resolve position
        int hudX, hudY;
        if (cfg.hudX >= 0 && cfg.hudY >= 0) {
            hudX = cfg.hudX;
            hudY = cfg.hudY;
        } else {
            String pos = cfg.hudPosition;
            switch (pos) {
                case "TOP_LEFT":
                    hudX = MARGIN;
                    hudY = MARGIN;
                    break;
                case "TOP_RIGHT":
                    hudX = screenW - HUD_W - MARGIN;
                    hudY = MARGIN;
                    break;
                case "BOTTOM_LEFT":
                    hudX = MARGIN;
                    hudY = screenH - HUD_H - MARGIN;
                    break;
                case "BOTTOM_RIGHT":
                    hudX = screenW - HUD_W - MARGIN;
                    hudY = screenH - HUD_H - MARGIN;
                    break;
                case "TOP_CENTER":
                default:
                    hudX = (screenW - HUD_W) / 2;
                    hudY = MARGIN;
                    break;
            }
        }

        // Unified slide animation for panel + all content together
        float slideOffset = (1f - AnimationHelper.easeOut(showProgress)) * -20f;
        if (cfg.hudX < 0 && cfg.hudY < 0) {
            String pos = cfg.hudPosition;
            if (pos.contains("BOTTOM")) {
                hudY -= (int) slideOffset;
            } else {
                hudY += (int) slideOffset;
            }
        } else {
            hudY += (int) slideOffset;
        }

        // Single alpha for EVERYTHING - panel, glow, text, waveform all share this
        float alpha = AnimationHelper.easeInOut(showProgress);

        // ── Update waveform animation ──────────────────────────────────────
        long now = System.currentTimeMillis();
        if (state.isPlaying() && now - lastWaveUpdate > 80) {
            lastWaveUpdate = now;
            for (int i = 0; i < WAVE_BARS; i++) {
                waveTargets[i] = WAVE_MIN_H + (float) Math.random() * (WAVE_MAX_H - WAVE_MIN_H);
            }
        } else if (!state.isPlaying()) {
            for (int i = 0; i < WAVE_BARS; i++) {
                waveTargets[i] = WAVE_MIN_H;
            }
        }
        for (int i = 0; i < WAVE_BARS; i++) {
            waveHeights[i] = AnimationHelper.approach(waveHeights[i], waveTargets[i], 12f, delta);
        }

        // ── Glow layers (rounded) ────────────────────────────────────────
        if (glowPulse > 0.01f) {
            float pulse = 0.7f + 0.3f * (float) Math.sin(now / 800.0);
            float glowStr = glowPulse * pulse;

            int softA = (int) (0x15 * glowStr * alpha);
            fillRounded(g, hudX - 3, hudY - 3, HUD_W + 6, HUD_H + 6, RADIUS + 3,
                    (softA << 24) | (GuiTheme.ACCENT & 0x00FFFFFF));

            int midA = (int) (0x35 * glowStr * alpha);
            fillRounded(g, hudX - 1, hudY - 1, HUD_W + 2, HUD_H + 2, RADIUS + 1,
                    (midA << 24) | (GuiTheme.ACCENT & 0x00FFFFFF));
        }

        // ── Main panel (rounded) ──────────────────────────────────────────
        int bgAlpha = (int) (0xE8 * alpha);
        fillRounded(g, hudX, hudY, HUD_W, HUD_H, RADIUS,
                (bgAlpha << 24) | (GuiTheme.PANEL & 0x00FFFFFF));

        // Subtle border (rounded)
        int borderAlpha = (int) (0x50 * alpha);
        drawRoundedBorder(g, hudX, hudY, HUD_W, HUD_H, RADIUS,
                (borderAlpha << 24) | (GuiTheme.ACCENT & 0x00FFFFFF));

        if (track == null) return;

        // All text colors use the SAME alpha as the panel
        int textAlpha = (int) (0xFF * alpha);
        int titleColor = (textAlpha << 24) | (GuiTheme.TEXT & 0x00FFFFFF);
        int artistColor = (textAlpha << 24) | (GuiTheme.TEXT_SOFT & 0x00FFFFFF);
        int sourceColor = (textAlpha << 24) | (GuiTheme.ACCENT & 0x00FFFFFF);

        // ── Left: Waveform visualizer ─────────────────────────────────────
        int waveX = hudX + PAD;
        int waveBaseY = hudY + HUD_H / 2;
        int totalWaveW = WAVE_BARS * (WAVE_BAR_W + WAVE_BAR_GAP) - WAVE_BAR_GAP;

        for (int i = 0; i < WAVE_BARS; i++) {
            int bx = waveX + i * (WAVE_BAR_W + WAVE_BAR_GAP);
            int bh = (int) waveHeights[i];
            int barTop = waveBaseY - bh / 2;
            float t = (float) i / WAVE_BARS;
            int barColor = lerpChannel(GuiTheme.ACCENT, GuiTheme.ACCENT_DARK, t);
            int drawColor = (textAlpha << 24) | (barColor & 0x00FFFFFF);
            g.fill(bx, barTop, bx + WAVE_BAR_W, barTop + bh, drawColor);
        }

        // ── Right: Track info ──────────────────────────────────────────────
        int infoX = waveX + totalWaveW + 8;
        int infoW = HUD_W - (infoX - hudX) - PAD;

        // Line 1: Title
        GuiRender.truncated(g, font, track.getTitle(), infoX, hudY + 3, infoW, titleColor);

        // Line 2: Artist · Source — fixed layout: artist left, source right-aligned
        String sourceLabel = getSourceLabel(track);
        String artist = track.getArtist();
        if (!sourceLabel.isEmpty()) {
            String sourceText = "\u00B7 " + sourceLabel;
            int sourceW = font.width(sourceText);
            // Source always right-aligned within infoW
            int sourceX = infoX + infoW - sourceW;
            // Artist takes remaining width (leave 4px gap)
            int artistOnlyW = Math.max(infoW - sourceW - 4, infoW / 2);
            GuiRender.truncated(g, font, artist, infoX, hudY + 14, artistOnlyW, artistColor);
            g.drawString(font, sourceText, sourceX, hudY + 14, sourceColor, true);
        } else {
            GuiRender.truncated(g, font, artist, infoX, hudY + 14, infoW, artistColor);
        }

        // ── Bottom: Thin progress line (seekable for non-NATIVE tracks) ────
        if (track.getPlaybackType() != PlaybackType.NATIVE && state.getDurationMs() > 0) {
            float progressPct = (float) state.getPositionMs() / state.getDurationMs();
            if (progressPct > 1f) progressPct = 1f;
            int progY = hudY + HUD_H - PROGRESS_H - 2;
            int progAlpha = (int) (0x60 * alpha);
            int trackBgColor = (progAlpha << 24) | (0x404040);
            int trackFillColor = (progAlpha << 24) | (GuiTheme.ACCENT & 0x00FFFFFF);
            g.fill(hudX + PAD, progY, hudX + HUD_W - PAD, progY + PROGRESS_H, trackBgColor);
            int fillW = (int) ((HUD_W - PAD * 2) * progressPct);
            if (fillW > 0) {
                g.fill(hudX + PAD, progY, hudX + PAD + fillW, progY + PROGRESS_H, trackFillColor);
            }
        }
    }

    /**
     * Build a key that changes on track/source change or loop restart.
     * Does NOT include play/pause state — that should not reset the animation.
     */
    private String buildStateKey(PlayerState state, TrackRef track) {
        String trackId = track != null ? track.getId() : "";
        String sourceId = track != null ? track.getSourceId() : "";

        // Detect loop restart: same track but position reset to near 0
        String loopKey = "";
        if (track != null && state.isPlaying() && state.getDurationMs() > 0) {
            if (lastKnownPosMs > state.getDurationMs() * 0.8 && state.getPositionMs() < 1000) {
                loopKey = "_loop_" + System.currentTimeMillis();
            }
        }

        return trackId + "|" + sourceId + loopKey;
    }

    /**
     * Map sourceId to a user-friendly source label.
     */
    private static String getSourceLabel(TrackRef track) {
        if (track == null) return "";
        String sid = track.getSourceId();
        if (sid == null || sid.isEmpty()) return "";
        switch (sid) {
            case "local": return "Local";
            case "youtube": return "YouTube";
            case "spotify": return "Spotify";
            case "soundcloud": return "SoundCloud";
            case "bandcamp": return "Bandcamp";
            case "vimeo": return "Vimeo";
            case "twitch": return "Twitch";
            case "http": return "Stream";
            default:
                // Capitalize first letter
                return sid.substring(0, 1).toUpperCase() + (sid.length() > 1 ? sid.substring(1) : "");
        }
    }

    private static void fillRounded(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (r <= 0) { g.fill(x, y, x + w, y + h, color); return; }
        r = Math.min(r, Math.min(w, h) / 2);
        // Center
        g.fill(x + r, y, x + w - r, y + h, color);
        // Left strip
        g.fill(x, y + r, x + r, y + h - r, color);
        // Right strip
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        // Corners (approximate with small fills)
        for (int i = 0; i < r; i++) {
            int dy = r - i;
            int dx = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            // Top-left
            g.fill(x + r - dx, y + i, x + r, y + i + 1, color);
            // Top-right
            g.fill(x + w - r, y + i, x + w - r + dx, y + i + 1, color);
            // Bottom-left
            g.fill(x + r - dx, y + h - i - 1, x + r, y + h - i, color);
            // Bottom-right
            g.fill(x + w - r, y + h - i - 1, x + w - r + dx, y + h - i, color);
        }
    }

    private static void drawRoundedBorder(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (r <= 0) {
            g.fill(x, y, x + w, y + 1, color);
            g.fill(x, y + h - 1, x + w, y + h, color);
            g.fill(x, y, x + 1, y + h, color);
            g.fill(x + w - 1, y, x + w, y + h, color);
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2);
        // Top edge
        g.fill(x + r, y, x + w - r, y + 1, color);
        // Bottom edge
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        // Left edge
        g.fill(x, y + r, x + 1, y + h - r, color);
        // Right edge
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);
        // Corner arcs
        for (int i = 0; i < r; i++) {
            int dy = r - i;
            int dx = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            int dxInner = (int) Math.sqrt(Math.max(0, (r - 1) * (r - 1) - dy * dy));
            // Top-left
            for (int px = r - dx; px < r - dxInner; px++) {
                g.fill(x + px, y + i, x + px + 1, y + i + 1, color);
            }
            // Top-right
            for (int px = w - r + dxInner; px < w - r + dx; px++) {
                g.fill(x + px, y + i, x + px + 1, y + i + 1, color);
            }
            // Bottom-left
            for (int px = r - dx; px < r - dxInner; px++) {
                g.fill(x + px, y + h - i - 1, x + px + 1, y + h - i, color);
            }
            // Bottom-right
            for (int px = w - r + dxInner; px < w - r + dx; px++) {
                g.fill(x + px, y + h - i - 1, x + px + 1, y + h - i, color);
            }
        }
    }

    private static int lerpChannel(int colorA, int colorB, float t) {
        int aR = (colorA >> 16) & 0xFF, aG = (colorA >> 8) & 0xFF, aB = colorA & 0xFF;
        int bR = (colorB >> 16) & 0xFF, bG = (colorB >> 8) & 0xFF, bB = colorB & 0xFF;
        int r = (int) (aR + (bR - aR) * t);
        int gr = (int) (aG + (bG - aG) * t);
        int b = (int) (aB + (bB - aB) * t);
        return (r << 16) | (gr << 8) | b;
    }

}
