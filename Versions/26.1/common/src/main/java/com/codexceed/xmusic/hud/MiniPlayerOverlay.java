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
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Clean, minimal HUD overlay for in-game playback status.
 * Design: Dark rounded pill with accent progress, waveform dots,
 * and track info. All elements animate as ONE unified unit.
 */
public class MiniPlayerOverlay {

    // â”€â”€ Layout â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private static final int HUD_W = 200;
    private static final int HUD_H = 36;
    private static final int MARGIN = 8;
    private static final int PAD = 8;
    private static final int RADIUS = 7;

    // Waveform (compact dots, not bars)
    private static final int WAVE_DOTS = 12;
    private static final int WAVE_DOT_SIZE = 2;
    private static final int WAVE_DOT_GAP = 2;
    private static final int WAVE_MAX_H = 14;
    private static final int WAVE_MIN_H = 2;

    // Progress
    private static final int PROGRESS_H = 2;
    private static final int PROGRESS_INSET = 6;

    // â”€â”€ Animation State â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private float showProgress = 0f;
    private long lastActivityTime = 0;
    private String lastStateKey = "";
    private float glowPulse = 0f;
    private final float[] waveHeights = new float[WAVE_DOTS];
    private final float[] waveTargets = new float[WAVE_DOTS];
    private long lastWaveUpdate = 0;
    private long lastKnownPosMs = 0;
    private boolean draggingProgress = false;

    public int getHudWidth() { return HUD_W; }
    public int getHudHeight() { return HUD_H; }
    public boolean isDraggingProgress() { return draggingProgress; }

    // â”€â”€ Mouse Input â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        XMusicConfig cfg = ConfigManager.get();
        if (!cfg.hudEnabled || showProgress < 0.5f) return false;

        PlayerState state = PlayerFacade.getInstance().snapshot();
        TrackRef track = state.getCurrentTrack();
        if (track == null || track.getPlaybackType() == PlaybackType.NATIVE) return false;
        if (state.getDurationMs() <= 0) return false;

        int hudX = resolveHudX(cfg);
        int hudY = resolveHudY(cfg);
        int progY = hudY + HUD_H - PROGRESS_H - 3;

        if (mouseX >= hudX && mouseX <= hudX + HUD_W && mouseY >= progY - 3 && mouseY <= hudY + HUD_H) {
            draggingProgress = true;
            seekFromMouse(mouseX, hudX, state);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!draggingProgress) return false;
        XMusicConfig cfg = ConfigManager.get();
        int hudX = resolveHudX(cfg);
        PlayerState state = PlayerFacade.getInstance().snapshot();
        seekFromMouse(mouseX, hudX, state);
        return true;
    }

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

    // â”€â”€ Main Render â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public void render(GuiGraphicsExtractor g, float partialTick) {
        XMusicConfig cfg = ConfigManager.get();
        if (!cfg.hudEnabled) return;

        PlayerState state = PlayerFacade.getInstance().snapshot();
        TrackRef track = state.getCurrentTrack();
        boolean isActive = state.isPlaying() || state.isPaused();

        // Detect track changes
        String stateKey = buildStateKey(state, track);
        if (!stateKey.equals(lastStateKey)) {
            lastStateKey = stateKey;
            lastActivityTime = System.currentTimeMillis();
            if (isActive) showProgress = 0f;
        }

        // Auto-hide
        boolean shouldShow = isActive;
        int autoHide = cfg.hudAutoHideSeconds;
        if (autoHide > 0 && isActive) {
            if (System.currentTimeMillis() - lastActivityTime > autoHide * 1000L)
                shouldShow = false;
        }

        // Animate
        float target = shouldShow ? 1f : 0f;
        float delta = partialTick / 20f;
        showProgress = AnimationHelper.approach(showProgress, target, 8f, delta);
        if (showProgress < 0.005f) return;

        if (track != null) lastKnownPosMs = state.getPositionMs();

        // Glow pulse
        if (state.isPlaying()) {
            glowPulse = AnimationHelper.approach(glowPulse, 1f, 3f, delta);
        } else {
            glowPulse = AnimationHelper.approach(glowPulse, 0f, 5f, delta);
        }

        Font font = Minecraft.getInstance().font;
        int hudX = resolveHudX(cfg);
        int hudY = resolveHudY(cfg);

        // Slide animation
        float slideAmount = (1f - showProgress) * -14f;
        if (cfg.hudX < 0 && cfg.hudY < 0) {
            if (cfg.hudPosition.contains("BOTTOM")) {
                hudY -= (int) slideAmount;
            } else {
                hudY += (int) slideAmount;
            }
        } else {
            hudY += (int) slideAmount;
        }

        float alpha = showProgress;
        long now = System.currentTimeMillis();

        // â”€â”€ Waveform animation â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        if (state.isPlaying() && now - lastWaveUpdate > 80) {
            lastWaveUpdate = now;
            for (int i = 0; i < WAVE_DOTS; i++) {
                float phase = (float) Math.sin(now / 220.0 + i * 0.8) * 0.5f + 0.5f;
                float env = 1f - Math.abs(i - WAVE_DOTS / 2f) / (WAVE_DOTS / 2f) * 0.35f;
                waveTargets[i] = WAVE_MIN_H + (WAVE_MAX_H - WAVE_MIN_H) * phase * env;
            }
        } else if (!state.isPlaying()) {
            for (int i = 0; i < WAVE_DOTS; i++) waveTargets[i] = WAVE_MIN_H;
        }
        for (int i = 0; i < WAVE_DOTS; i++) {
            waveHeights[i] = AnimationHelper.approach(waveHeights[i], waveTargets[i], 12f, delta);
        }

        // â”€â”€ Glow (subtle, only when playing) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        if (glowPulse > 0.01f) {
            float pulse = 0.8f + 0.2f * (float) Math.sin(now / 1000.0);
            float gs = glowPulse * pulse;
            int outerA = (int) (0x0C * gs * alpha);
            fillRounded(g, hudX - 3, hudY - 3, HUD_W + 6, HUD_H + 6, RADIUS + 3,
                    (outerA << 24) | (GuiTheme.ACCENT & 0x00FFFFFF));
            int innerA = (int) (0x20 * gs * alpha);
            fillRounded(g, hudX - 1, hudY - 1, HUD_W + 2, HUD_H + 2, RADIUS + 1,
                    (innerA << 24) | (GuiTheme.ACCENT & 0x00FFFFFF));
        }

        // â”€â”€ Panel â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        int bgAlpha = (int) (0xE5 * alpha);
        fillRounded(g, hudX, hudY, HUD_W, HUD_H, RADIUS,
                (bgAlpha << 24) | 0x1E1E1E);

        // Border
        int borderAlpha = (int) (0x30 * alpha);
        drawRoundedBorder(g, hudX, hudY, HUD_W, HUD_H, RADIUS,
                (borderAlpha << 24) | 0x505050);

        // Top highlight
        int hlAlpha = (int) (0x10 * alpha);
        g.fill(hudX + RADIUS, hudY + 1, hudX + HUD_W - RADIUS, hudY + 2,
                (hlAlpha << 24) | 0xFFFFFF);

        if (track == null) {
            int mutedAlpha = (int) (0xFF * alpha);
            GuiRender.shadowText(g, font, "No track playing",
                    hudX + PAD, hudY + HUD_H / 2 - 4,
                    (mutedAlpha << 24) | (GuiTheme.TEXT_MUTED & 0x00FFFFFF));
            return;
        }

        int textAlpha = (int) (0xFF * alpha);

        // â”€â”€ Waveform (left) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        int waveX = hudX + PAD;
        int waveBaseY = hudY + (HUD_H - PROGRESS_H) / 2 - 1;
        int totalWaveW = WAVE_DOTS * (WAVE_DOT_SIZE + WAVE_DOT_GAP) - WAVE_DOT_GAP;

        for (int i = 0; i < WAVE_DOTS; i++) {
            int bx = waveX + i * (WAVE_DOT_SIZE + WAVE_DOT_GAP);
            int bh = Math.max(1, (int) waveHeights[i]);
            int barTop = waveBaseY - bh / 2;
            float t = (float) i / WAVE_DOTS;
            int barRGB = AnimationHelper.lerpColor(
                    GuiTheme.ACCENT & 0x00FFFFFF,
                    GuiTheme.ACCENT_DARK & 0x00FFFFFF, t);
            g.fill(bx, barTop, bx + WAVE_DOT_SIZE, barTop + bh,
                    (textAlpha << 24) | (barRGB & 0x00FFFFFF));
        }

        // â”€â”€ Track info (right of waveform) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        int infoX = waveX + totalWaveW + 7;
        int infoW = HUD_W - (infoX - hudX) - PAD;

        // Title
        String title = track.getTitle();
        if (title == null || title.isEmpty()) title = "Unknown";
        int titleColor = (textAlpha << 24) | (GuiTheme.TEXT & 0x00FFFFFF);
        GuiRender.truncated(g, font, title, infoX, hudY + 5, infoW, titleColor);

        // Artist Â· Source
        String artist = track.getArtist();
        if (artist == null || artist.isEmpty()) artist = "Unknown Artist";
        String sourceLabel = getSourceLabel(track);
        int artistColor = (textAlpha << 24) | (GuiTheme.TEXT_SOFT & 0x00FFFFFF);
        int sourceColor = (textAlpha << 24) | (GuiTheme.ACCENT & 0x00FFFFFF);

        if (!sourceLabel.isEmpty()) {
            String sourceText = "\u00B7 " + sourceLabel;
            int sourceW = font.width(sourceText);
            int sourceX = infoX + infoW - sourceW;
            int artistW = Math.max(infoW - sourceW - 4, infoW / 2);
            GuiRender.truncated(g, font, artist, infoX, hudY + 16, artistW, artistColor);
            g.text(font, sourceText, sourceX, hudY + 16, sourceColor, true);
        } else {
            GuiRender.truncated(g, font, artist, infoX, hudY + 16, infoW, artistColor);
        }

        // â”€â”€ Progress bar (bottom) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        if (track.getPlaybackType() != PlaybackType.NATIVE && state.getDurationMs() > 0) {
            float progressPct = (float) state.getPositionMs() / state.getDurationMs();
            if (progressPct > 1f) progressPct = 1f;
            int progY = hudY + HUD_H - PROGRESS_H - 3;

            // Track
            int trackBgAlpha = (int) (0x30 * alpha);
            g.fill(hudX + PROGRESS_INSET, progY, hudX + HUD_W - PROGRESS_INSET, progY + PROGRESS_H,
                    (trackBgAlpha << 24) | 0x505050);

            // Fill
            int fillW = (int) ((HUD_W - PROGRESS_INSET * 2) * progressPct);
            if (fillW > 0) {
                int fillAlpha = (int) (0xB0 * alpha);
                g.fill(hudX + PROGRESS_INSET, progY, hudX + PROGRESS_INSET + fillW, progY + PROGRESS_H,
                        (fillAlpha << 24) | (GuiTheme.ACCENT & 0x00FFFFFF));
            }
        }
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private String buildStateKey(PlayerState state, TrackRef track) {
        String trackId = track != null ? track.getId() : "";
        String sourceId = track != null ? track.getSourceId() : "";
        String loopKey = "";
        if (track != null && state.isPlaying() && state.getDurationMs() > 0) {
            if (lastKnownPosMs > state.getDurationMs() * 0.8 && state.getPositionMs() < 1000) {
                loopKey = "_loop_" + System.currentTimeMillis();
            }
        }
        return trackId + "|" + sourceId + loopKey;
    }

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
                return sid.substring(0, 1).toUpperCase() + (sid.length() > 1 ? sid.substring(1) : "");
        }
    }

    // â”€â”€ Rounded rect primitives â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private static void fillRounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        if (r <= 0) { g.fill(x, y, x + w, y + h, color); return; }
        r = Math.min(r, Math.min(w, h) / 2);
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int dy = r - i;
            int dx = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            g.fill(x + r - dx, y + i, x + r, y + i + 1, color);
            g.fill(x + w - r, y + i, x + w - r + dx, y + i + 1, color);
            g.fill(x + r - dx, y + h - i - 1, x + r, y + h - i, color);
            g.fill(x + w - r, y + h - i - 1, x + w - r + dx, y + h - i, color);
        }
    }

    private static void drawRoundedBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        if (r <= 0) {
            g.fill(x, y, x + w, y + 1, color);
            g.fill(x, y + h - 1, x + w, y + h, color);
            g.fill(x, y, x + 1, y + h, color);
            g.fill(x + w - 1, y, x + w, y + h, color);
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2);
        g.fill(x + r, y, x + w - r, y + 1, color);
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        g.fill(x, y + r, x + 1, y + h - r, color);
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int dy = r - i;
            int dx = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            int dxInner = (int) Math.sqrt(Math.max(0, (r - 1) * (r - 1) - dy * dy));
            for (int px = r - dx; px < r - dxInner; px++) {
                g.fill(x + px, y + i, x + px + 1, y + i + 1, color);
            }
            for (int px = w - r + dxInner; px < w - r + dx; px++) {
                g.fill(x + px, y + i, x + px + 1, y + i + 1, color);
            }
            for (int px = r - dx; px < r - dxInner; px++) {
                g.fill(x + px, y + h - i - 1, x + px + 1, y + h - i, color);
            }
            for (int px = w - r + dxInner; px < w - r + dx; px++) {
                g.fill(x + px, y + h - i - 1, x + px + 1, y + h - i, color);
            }
        }
    }
}
