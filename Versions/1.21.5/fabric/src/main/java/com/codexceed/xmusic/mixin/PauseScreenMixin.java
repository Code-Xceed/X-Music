package com.codexceed.xmusic.mixin;

import com.codexceed.xmusic.gui.render.ArtworkRenderer;
import com.codexceed.xmusic.gui.render.GuiRender;
import com.codexceed.xmusic.gui.theme.GuiTheme;
import com.codexceed.xmusic.player.PlayerFacade;
import com.codexceed.xmusic.player.PlayerState;
import com.codexceed.xmusic.source.PlaybackType;
import com.codexceed.xmusic.source.TrackRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders a modern mini-player widget at the top of the pause/escape screen.
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {

    private static final int WIDGET_W = 260;
    private static final int WIDGET_H = 48;
    private static final int RADIUS = 6;
    private static final int PAD = 10;
    private static final int PROGRESS_H = 3;
    private static final int ART_SIZE = 36;

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        PlayerState state = PlayerFacade.getInstance().snapshot();
        TrackRef track = state.getCurrentTrack();
        if (track == null && !state.isPlaying() && !state.isPaused()) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth();

        int widgetX = (screenW - WIDGET_W) / 2;
        int widgetY = 10;

        // Background panel
        fillRounded(g, widgetX, widgetY, WIDGET_W, WIDGET_H, RADIUS, 0xE8222222);
        drawRoundedBorder(g, widgetX, widgetY, WIDGET_W, WIDGET_H, RADIUS, 0x503BF0FF);

        if (track == null) return;

        // Left side: Album art
        int artX = widgetX + PAD;
        int artY = widgetY + (WIDGET_H - ART_SIZE) / 2;
        ArtworkRenderer.renderArtwork(g, track.getArtworkUrl(), artX, artY, ART_SIZE);

        // Right of art: track info
        int infoX = artX + ART_SIZE + 8;
        int infoW = WIDGET_W - (infoX - widgetX) - PAD;

        int titleColor = GuiTheme.TEXT;
        int artistColor = GuiTheme.TEXT_SOFT;
        int sourceColor = GuiTheme.ACCENT;

        // Line 1: Title
        GuiRender.truncated(g, font, track.getTitle(), infoX, widgetY + 5, infoW, titleColor);

        // Line 2: Artist · Source — fixed layout: artist left, source right-aligned
        String sourceLabel = getSourceLabel(track);
        String artist = track.getArtist() != null ? track.getArtist() : "";
        if (!sourceLabel.isEmpty()) {
            String sourceText = "\u00B7 " + sourceLabel;
            int sourceW = font.width(sourceText);
            int sourceX = infoX + infoW - sourceW;
            int artistOnlyW = Math.max(infoW - sourceW - 4, infoW / 2);
            GuiRender.truncated(g, font, artist, infoX, widgetY + 16, artistOnlyW, artistColor);
            g.drawString(font, sourceText, sourceX, widgetY + 16, sourceColor, true);
        } else {
            GuiRender.truncated(g, font, artist, infoX, widgetY + 16, infoW, artistColor);
        }

        // Line 3: Play state + position
        String stateText = state.isPlaying() ? "\u25B6 Playing" : (state.isPaused() ? "\u275A\u275A Paused" : "\u25A0 Stopped");
        long posMs = state.getPositionMs();
        long durMs = state.getDurationMs();
        String posText = formatTime(posMs) + " / " + formatTime(durMs);
        g.drawString(font, stateText, infoX, widgetY + 27, GuiTheme.TEXT_MUTED, true);
        g.drawString(font, posText, infoX + infoW - font.width(posText), widgetY + 27, GuiTheme.TEXT_MUTED, true);

        // Progress bar (for non-NATIVE tracks with duration)
        if (track.getPlaybackType() != PlaybackType.NATIVE && durMs > 0) {
            float pct = (float) posMs / durMs;
            if (pct > 1f) pct = 1f;
            int progY = widgetY + WIDGET_H - PROGRESS_H - 3;
            g.fill(widgetX + PAD, progY, widgetX + WIDGET_W - PAD, progY + PROGRESS_H, 0x40404040);
            int fillW = (int) ((WIDGET_W - PAD * 2) * pct);
            if (fillW > 0) {
                g.fill(widgetX + PAD, progY, widgetX + PAD + fillW, progY + PROGRESS_H, GuiTheme.ACCENT);
            }
        }
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
            default: return sid.substring(0, 1).toUpperCase() + (sid.length() > 1 ? sid.substring(1) : "");
        }
    }

    private static String formatTime(long ms) {
        int sec = (int) (ms / 1000);
        int m = sec / 60;
        int s = sec % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }

    private static void fillRounded(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (r <= 0) { g.fill(x, y, x + w, y + h, color); return; }
        r = Math.min(r, Math.min(w, h) / 2);
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int dy = r - i;
            int a = (int) (Math.sqrt(r * r - dy * dy) / r * r);
            g.fill(x + r - a, y + i, x + r, y + i + 1, color);
            g.fill(x + w - r, y + i, x + w - r + a, y + i + 1, color);
            g.fill(x + r - a, y + h - i - 1, x + r, y + h - i, color);
            g.fill(x + w - r, y + h - i - 1, x + w - r + a, y + h - i, color);
        }
    }

    private static void drawRoundedBorder(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        // Top
        g.fill(x + r, y, x + w - r, y + 1, color);
        // Bottom
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        // Left
        g.fill(x, y + r, x + 1, y + h - r, color);
        // Right
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);
    }
}
