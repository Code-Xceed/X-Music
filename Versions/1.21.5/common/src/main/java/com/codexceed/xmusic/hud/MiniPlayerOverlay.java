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
 * Clean, minimal HUD overlay for in-game playback status.
 * Design: Dark rounded pill with accent progress, waveform dots,
 * and track info. All elements animate as ONE unified unit.
 */
public class MiniPlayerOverlay {

    // ── Layout ───────────────────────────────────────────────────────────
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

    // ── Animation & Transition State ─────────────────────────────────────
    private float showProgress = 0f;
    private long lastActivityTime = 0;
    private float glowPulse = 0f;
    private final float[] waveHeights = new float[WAVE_DOTS];
    private final float[] waveTargets = new float[WAVE_DOTS];
    private long lastWaveUpdate = 0;

    private TrackRef renderedTrack = null;
    private int lastKnownLoopIteration = 0;
    private long lastKnownPosMs = 0;
    private float lockedProgressPct = 0f;
    private boolean isTransitioningOut = false;
    private boolean draggingProgress = false;

    public int getHudWidth() { return HUD_W; }
    public int getHudHeight() { return HUD_H; }
    public boolean isDraggingProgress() { return draggingProgress; }

    // ── Mouse Input ──────────────────────────────────────────────────────

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

    // ── Main Render ──────────────────────────────────────────────────────

    public void render(GuiGraphics g, float partialTick) {
        XMusicConfig cfg = ConfigManager.get();
        if (!cfg.hudEnabled) return;

        PlayerState state = PlayerFacade.getInstance().snapshot();
        TrackRef track = state.getCurrentTrack();
        boolean isActive = state.isPlaying() || state.isPaused();

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
                // Check rewind/restart (exclude when dragging progress)
                else if (!draggingProgress && state.getPositionMs() < lastKnownPosMs - 1500) {
                    trackChanged = true;
                }
            }
        }

        // 2. Handle transition states
        if (trackChanged) {
            if (showProgress < 0.01f) {
                // If already invisible, swap instantly in the background
                renderedTrack = track;
                if (track != null) {
                    lastKnownLoopIteration = state.getLoopIteration();
                    lastKnownPosMs = state.getPositionMs();
                    lockedProgressPct = state.getDurationMs() > 0 ? (float) state.getPositionMs() / state.getDurationMs() : 0f;
                } else {
                    lastKnownLoopIteration = 0;
                    lastKnownPosMs = 0;
                    lockedProgressPct = 0f;
                }
                isTransitioningOut = false;
                lastActivityTime = System.currentTimeMillis();
            } else if (!isTransitioningOut) {
                // Start transitioning out (lock current progress percent to avoid visual jitter)
                isTransitioningOut = true;
                if (renderedTrack != null && state.getDurationMs() > 0) {
                    lockedProgressPct = (float) lastKnownPosMs / state.getDurationMs();
                } else {
                    lockedProgressPct = 0f;
                }
            }
        }

        // Force hide when transitioning out
        boolean shouldShow = isActive && !isTransitioningOut;
        int autoHide = cfg.hudAutoHideSeconds;
        if (autoHide > 0 && isActive && !isTransitioningOut) {
            if (System.currentTimeMillis() - lastActivityTime > autoHide * 1000L) {
                shouldShow = false;
            }
        }

        // Approach transition progress
        float target = shouldShow ? 1f : 0f;
        float delta = partialTick / 20f;
        showProgress = AnimationHelper.approach(showProgress, target, 8f, delta);

        // Complete transition once fully hidden
        if (showProgress < 0.01f) {
            if (isTransitioningOut) {
                renderedTrack = track;
                if (track != null) {
                    lastKnownLoopIteration = state.getLoopIteration();
                    lastKnownPosMs = state.getPositionMs();
                    lockedProgressPct = state.getDurationMs() > 0 ? (float) state.getPositionMs() / state.getDurationMs() : 0f;
                } else {
                    lastKnownLoopIteration = 0;
                    lastKnownPosMs = 0;
                    lockedProgressPct = 0f;
                }
                isTransitioningOut = false;
                lastActivityTime = System.currentTimeMillis();
            }
            return;
        }

        // 3. Keep tracking position/loop iteration under normal playback
        if (!isTransitioningOut && track != null) {
            lastKnownPosMs = state.getPositionMs();
            lastKnownLoopIteration = state.getLoopIteration();
        }

        // Glow pulse
        if (state.isPlaying() && !isTransitioningOut) {
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

        // ── Waveform animation ───────────────────────────────────────────
        if (state.isPlaying() && !isTransitioningOut && now - lastWaveUpdate > 80) {
            lastWaveUpdate = now;
            for (int i = 0; i < WAVE_DOTS; i++) {
                float phase = (float) Math.sin(now / 220.0 + i * 0.8) * 0.5f + 0.5f;
                float env = 1f - Math.abs(i - WAVE_DOTS / 2f) / (WAVE_DOTS / 2f) * 0.35f;
                waveTargets[i] = WAVE_MIN_H + (WAVE_MAX_H - WAVE_MIN_H) * phase * env;
            }
        } else if (!state.isPlaying() || isTransitioningOut) {
            for (int i = 0; i < WAVE_DOTS; i++) waveTargets[i] = WAVE_MIN_H;
        }
        for (int i = 0; i < WAVE_DOTS; i++) {
            waveHeights[i] = AnimationHelper.approach(waveHeights[i], waveTargets[i], 12f, delta);
        }

        // ── Glow (subtle, only when playing) ─────────────────────────────
        if (glowPulse > 0.01f) {
            float pulse = 0.8f + 0.2f * (float) Math.sin(now / 1000.0);
            float gs = glowPulse * pulse;
            int outerA = (int) (0x0C * gs * alpha);
            GuiRender.fillRounded(g, hudX - 3, hudY - 3, HUD_W + 6, HUD_H + 6, RADIUS + 3,
                    (outerA << 24) | (GuiTheme.ACCENT & 0x00FFFFFF));
            int innerA = (int) (0x20 * gs * alpha);
            GuiRender.fillRounded(g, hudX - 1, hudY - 1, HUD_W + 2, HUD_H + 2, RADIUS + 1,
                    (innerA << 24) | (GuiTheme.ACCENT & 0x00FFFFFF));
        }

        // ── Panel ────────────────────────────────────────────────────────
        int bgAlpha = (int) (0xEE * alpha);
        // Slate background panel
        GuiRender.fillRounded(g, hudX, hudY, HUD_W, HUD_H, RADIUS,
                (bgAlpha << 24) | (GuiTheme.PANEL & 0x00FFFFFF));

        // Border (Electric cyan soft glow outline)
        int borderAlpha = (int) (0x40 * alpha);
        GuiRender.drawRoundedBorder(g, hudX, hudY, HUD_W, HUD_H, RADIUS,
                (borderAlpha << 24) | (GuiTheme.ACCENT_DARK & 0x00FFFFFF));

        // Top highlight
        int hlAlpha = (int) (0x15 * alpha);
        g.fill(hudX + RADIUS, hudY + 1, hudX + HUD_W - RADIUS, hudY + 2,
                (hlAlpha << 24) | 0xFFFFFF);

        if (renderedTrack == null) {
            int mutedAlpha = (int) (0xFF * alpha);
            GuiRender.shadowText(g, font, "No track playing",
                    hudX + PAD, hudY + HUD_H / 2 - 4,
                    (mutedAlpha << 24) | (GuiTheme.TEXT_MUTED & 0x00FFFFFF));
            return;
        }

        int textAlpha = (int) (0xFF * alpha);

        // ── Waveform (left) ──────────────────────────────────────────────
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

        // ── Track info (right of waveform) ───────────────────────────────
        int infoX = waveX + totalWaveW + 7;
        int infoW = HUD_W - (infoX - hudX) - PAD;

        // Title
        String title = renderedTrack.getTitle();
        if (title == null || title.isEmpty()) title = "Unknown";
        int titleColor = (textAlpha << 24) | (GuiTheme.TEXT & 0x00FFFFFF);
        GuiRender.truncated(g, font, title, infoX, hudY + 5, infoW, titleColor);

        // Artist · Source
        String artist = renderedTrack.getArtist();
        if (artist == null || artist.isEmpty()) artist = "Unknown Artist";
        String sourceLabel = getSourceLabel(renderedTrack);
        int artistColor = (textAlpha << 24) | (GuiTheme.TEXT_SOFT & 0x00FFFFFF);
        int sourceColor = (textAlpha << 24) | (GuiTheme.ACCENT & 0x00FFFFFF);

        if (!sourceLabel.isEmpty()) {
            String sourceText = "\u00B7 " + sourceLabel;
            int sourceW = font.width(sourceText);
            int sourceX = infoX + infoW - sourceW;
            int artistW = Math.max(infoW - sourceW - 4, infoW / 2);
            GuiRender.truncated(g, font, artist, infoX, hudY + 16, artistW, artistColor);
            g.drawString(font, sourceText, sourceX, hudY + 16, sourceColor, true);
        } else {
            GuiRender.truncated(g, font, artist, infoX, hudY + 16, infoW, artistColor);
        }

        // ── Progress bar (bottom) ────────────────────────────────────────
        if (renderedTrack.getPlaybackType() != PlaybackType.NATIVE && (isTransitioningOut ? true : state.getDurationMs() > 0)) {
            float progressPct = 0f;
            if (draggingProgress) {
                progressPct = state.getDurationMs() > 0 ? (float) state.getPositionMs() / state.getDurationMs() : 0f;
            } else if (isTransitioningOut) {
                progressPct = lockedProgressPct;
            } else {
                progressPct = state.getDurationMs() > 0 ? (float) state.getPositionMs() / state.getDurationMs() : 0f;
            }

            if (progressPct > 1f) progressPct = 1f;
            int progY = hudY + HUD_H - PROGRESS_H - 3;

            // Track background
            int trackBgAlpha = (int) (0x25 * alpha);
            GuiRender.fillRounded(g, hudX + PROGRESS_INSET, progY, HUD_W - PROGRESS_INSET * 2, PROGRESS_H, 1,
                    (trackBgAlpha << 24) | (GuiTheme.ACCENT_DARK & 0x00FFFFFF));

            // Fill
            int fillW = (int) ((HUD_W - PROGRESS_INSET * 2) * progressPct);
            if (fillW > 0) {
                int fillAlpha = (int) (0xB0 * alpha);
                GuiRender.fillRounded(g, hudX + PROGRESS_INSET, progY, fillW, PROGRESS_H, 1,
                        (fillAlpha << 24) | (GuiTheme.ACCENT & 0x00FFFFFF));
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

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
}
