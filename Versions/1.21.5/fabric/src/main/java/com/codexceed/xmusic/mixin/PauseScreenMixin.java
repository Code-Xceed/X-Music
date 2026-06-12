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
 * Renders a modern mini-player widget at the bottom of the pause/escape screen.
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {

    private static final int WIDGET_W = 240;
    private static final int WIDGET_H = 44;
    private static final int RADIUS = 6;
    private static final int PAD = 10;
    private static final int PROGRESS_H = 2;
    private static final int ART_SIZE = 26;

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        PlayerState state = PlayerFacade.getInstance().snapshot();
        TrackRef track = state.getCurrentTrack();
        boolean widgetActive = (track != null || state.isPlaying() || state.isPaused());

        if (widgetActive) {
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;
            int screenW = mc.getWindow().getGuiScaledWidth();
            int screenH = mc.getWindow().getGuiScaledHeight();

            int widgetX = (screenW - WIDGET_W) / 2;
            int widgetY = screenH - WIDGET_H - 12; // Positioned at the bottom of the screen with 12px margin

            // Glow effect (Subtle atmospheric pulsing glow when music is playing)
            if (state.isPlaying()) {
                long now = System.currentTimeMillis();
                float pulse = 0.8f + 0.2f * (float) Math.sin(now / 1000.0);
                int outerA = (int) (0x0B * pulse);
                GuiRender.fillRounded(g, widgetX - 3, widgetY - 3, WIDGET_W + 6, WIDGET_H + 6, RADIUS + 3,
                        (outerA << 24) | (GuiTheme.ACCENT & 0x00FFFFFF));
                int innerA = (int) (0x1C * pulse);
                GuiRender.fillRounded(g, widgetX - 1, widgetY - 1, WIDGET_W + 2, WIDGET_H + 2, RADIUS + 1,
                        (innerA << 24) | (GuiTheme.ACCENT & 0x00FFFFFF));
            }

            // Background panel (Slate dark gradient style)
            GuiRender.fillRounded(g, widgetX, widgetY, WIDGET_W, WIDGET_H, RADIUS,
                    (0xEE << 24) | (GuiTheme.PANEL & 0x00FFFFFF));

            // Border (Electric cyan soft outline)
            GuiRender.drawRoundedBorder(g, widgetX, widgetY, WIDGET_W, WIDGET_H, RADIUS,
                    (0x40 << 24) | (GuiTheme.ACCENT_DARK & 0x00FFFFFF));

            // Top highlight
            g.fill(widgetX + RADIUS, widgetY + 1, widgetX + WIDGET_W - RADIUS, widgetY + 2,
                    (0x15 << 24) | 0xFFFFFF);

            if (track != null) {
                // Left side: Album art (centered vertically in content area)
                int artX = widgetX + PAD;
                int artY = widgetY + 5;
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
                    GuiRender.truncated(g, font, artist, infoX, widgetY + 15, artistOnlyW, artistColor);
                    g.drawString(font, sourceText, sourceX, widgetY + 15, sourceColor, true);
                } else {
                    GuiRender.truncated(g, font, artist, infoX, widgetY + 15, infoW, artistColor);
                }

                // Line 3: Play state + position
                String stateText = state.isPlaying() ? "\u25B6 Playing" : (state.isPaused() ? "\u275A\u275A Paused" : "\u25A0 Stopped");
                long posMs = state.getPositionMs();
                long durMs = state.getDurationMs();
                String posText = formatTime(posMs) + " / " + formatTime(durMs);
                g.drawString(font, stateText, infoX, widgetY + 25, GuiTheme.TEXT_MUTED, true);
                g.drawString(font, posText, infoX + infoW - font.width(posText), widgetY + 25, GuiTheme.TEXT_MUTED, true);

                // Progress bar (for non-NATIVE tracks with duration)
                if (track.getPlaybackType() != PlaybackType.NATIVE && durMs > 0) {
                    float pct = (float) posMs / durMs;
                    if (pct > 1f) pct = 1f;
                    int progY = widgetY + 36;

                    // Rounded Track background
                    GuiRender.fillRounded(g, widgetX + PAD, progY, WIDGET_W - PAD * 2, PROGRESS_H, 1, 0x30505050);

                    int fillW = (int) ((WIDGET_W - PAD * 2) * pct);
                    if (fillW > 0) {
                        GuiRender.fillRounded(g, widgetX + PAD, progY, fillW, PROGRESS_H, 1,
                                (0xB0 << 24) | (GuiTheme.ACCENT & 0x00FFFFFF));
                    }
                }
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
}
