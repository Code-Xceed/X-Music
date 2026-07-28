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
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    private static final int ART_W = 54;
    private static final int ART_H = 30;

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        PlayerState state = PlayerFacade.getInstance().snapshot();
        TrackRef track = state.getCurrentTrack();
        boolean widgetActive = (track != null || state.isPlaying() || state.isPaused());

        if (widgetActive) {
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;
            int screenW = mc.getWindow().getGuiScaledWidth();
            int screenH = mc.getWindow().getGuiScaledHeight();

            int widgetX = (screenW - WIDGET_W) / 2;

            // Dynamically find the lowest button in the pause menu column
            int lowestY = -1;
            int lowestH = 0;
            net.minecraft.client.gui.screens.Screen screen = (net.minecraft.client.gui.screens.Screen) (Object) this;
            for (net.minecraft.client.gui.components.events.GuiEventListener child : screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                    int centerX = widget.getX() + widget.getWidth() / 2;
                    if (Math.abs(centerX - screenW / 2) < 60 && widget.getY() > screenH / 2) {
                        if (widget.getY() > lowestY) {
                            lowestY = widget.getY();
                            lowestH = widget.getHeight();
                        }
                    }
                }
            }

            int widgetY = screenH - WIDGET_H - 12;
            if (lowestY != -1) {
                widgetY = Math.min(screenH - WIDGET_H - 8, lowestY + lowestH + 8);
            }

            // Glow effect (Subtle square pulsing glow when music is playing)
            if (state.isPlaying()) {
                long now = System.currentTimeMillis();
                float pulse = 0.8f + 0.2f * (float) Math.sin(now / 1000.0);
                int outerA = (int) (0x0C * pulse);
                g.fill(widgetX - 3, widgetY - 3, widgetX + WIDGET_W + 3, widgetY + WIDGET_H + 3,
                        (outerA << 24) | (GuiTheme.ACCENT & 0x00FFFFFF));
                int innerA = (int) (0x1A * pulse);
                g.fill(widgetX - 1, widgetY - 1, widgetX + WIDGET_W + 1, widgetY + WIDGET_H + 1,
                        (innerA << 24) | (GuiTheme.ACCENT & 0x00FFFFFF));
            }

            // Background panel (Slate dark gradient style)
            GuiRender.gradientV(g, widgetX, widgetY, WIDGET_W, WIDGET_H, GuiTheme.FRAME_TOP, GuiTheme.FRAME_BOTTOM);

            // Border (Exact same border as main GUI)
            GuiRender.mcFrameBorder(g, widgetX, widgetY, WIDGET_W, WIDGET_H);

            if (track != null) {
                // Left side: Album art (centered vertically in content area)
                int artX = widgetX + PAD;
                int artY = widgetY + 7;

                // Draw well box
                GuiRender.mcWell(g, artX - 1, artY - 1, ART_W + 2, ART_H + 2);
                if (track.getArtworkUrl() != null && !track.getArtworkUrl().isEmpty()) {
                    ArtworkRenderer.renderArtwork(g, track, artX, artY, ART_W, ART_H, 1.0f);
                } else {
                    ArtworkRenderer.renderPlaceholder(g, track, artX, artY, ART_W, ART_H, 1.0f);
                }

                // Pulsing animated border (sharp corners)
                long now = System.currentTimeMillis();
                float borderPulse = state.isPlaying() ? 0.6f + 0.4f * (float) Math.sin(now / 200.0) : 0.4f;
                int borderA = (int) (0xFF * borderPulse);
                int borderColor = (borderA << 24) | (GuiTheme.ACCENT & 0xFFFFFF);
                GuiRender.outline(g, artX - 1, artY - 1, ART_W + 2, ART_H + 2, borderColor);

                if (state.isPlaying()) {
                    // Soft glow around thumbnail boundary
                    GuiRender.smoothHoverGlow(g, artX - 2, artY - 2, ART_W + 4, ART_H + 4, borderPulse * 0.8f);
                }

                // Right of art: track info
                int infoX = artX + ART_W + 8;
                int infoW = WIDGET_W - (infoX - widgetX) - PAD;

                int titleColor = GuiTheme.TEXT;
                int artistColor = GuiTheme.TEXT_SOFT;
                int sourceColor = GuiTheme.ACCENT;

                // Line 1: Title
                GuiRender.truncated(g, font, track.getTitle(), infoX, widgetY + 6, infoW, titleColor);

                // Line 2: Artist · Source — fixed layout: artist left, source right-aligned
                String sourceLabel = getSourceLabel(track);
                String artist = track.getArtist() != null ? track.getArtist() : "";
                if (!sourceLabel.isEmpty()) {
                    String sourceText = "\u00B7 " + sourceLabel;
                    int sourceW = font.width(sourceText);
                    int sourceX = infoX + infoW - sourceW;
                    int artistOnlyW = Math.max(infoW - sourceW - 4, infoW / 2);
                    GuiRender.truncated(g, font, artist, infoX, widgetY + 16, artistOnlyW, artistColor);
                    g.text(font, sourceText, sourceX, widgetY + 16, sourceColor, true);
                } else {
                    GuiRender.truncated(g, font, artist, infoX, widgetY + 16, infoW, artistColor);
                }

                // Line 3: Play state + position
                String stateText = state.isPlaying() ? "\u25B6 Playing" : (state.isPaused() ? "\u275A\u275A Paused" : "\u25A0 Stopped");
                long posMs = state.getPositionMs();
                long durMs = state.getDurationMs();
                String posText = formatTime(posMs) + " / " + formatTime(durMs);
                g.text(font, stateText, infoX, widgetY + 26, GuiTheme.TEXT_MUTED, true);
                g.text(font, posText, infoX + infoW - font.width(posText), widgetY + 26, GuiTheme.TEXT_MUTED, true);

                // Progress bar directly at bottom border
                if (track.getPlaybackType() != PlaybackType.NATIVE && durMs > 0) {
                    float pct = (float) posMs / durMs;
                    if (pct > 1f) pct = 1f;
                    int barX = widgetX;
                    int barY = widgetY + WIDGET_H - 3;
                    int barW = WIDGET_W;
                    int barH = 3;

                    // Background well overwriting the frame border bottom shadow
                    g.fill(barX, barY, barX + barW, barY + barH, GuiTheme.FRAME_EDGE);

                    int fillW = (int) (barW * pct);
                    if (fillW > 0) {
                        g.fill(barX, barY, barX + fillW, barY + barH, GuiTheme.ACCENT);

                        // Laser head glow indicator at the leading edge
                        if (fillW < barW) {
                            int headX = barX + fillW;
                            float headPulse = 0.7f + 0.3f * (float) Math.sin(System.currentTimeMillis() / 150.0);
                            int headCol = (int) (0xFF * headPulse) << 24 | (GuiTheme.ACCENT_BRIGHT & 0x00FFFFFF);
                            g.fill(headX - 2, barY, headX + 1, barY + barH, headCol);
                            g.fill(headX - 1, barY, headX, barY + barH, 0xFFFFFFFF);
                        }
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
