package com.codexceed.xmusic.gui.component;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.audio.AudioEngine;
import com.codexceed.xmusic.gui.layout.GuiFrame;
import com.codexceed.xmusic.gui.render.GuiRender;
import com.codexceed.xmusic.gui.render.HoverTracker;
import com.codexceed.xmusic.gui.render.IconRenderer;
import com.codexceed.xmusic.gui.theme.GuiTheme;
import com.codexceed.xmusic.gui.util.AnimationHelper;
import com.codexceed.xmusic.player.PlayerFacade;
import com.codexceed.xmusic.player.PlayerState;
import com.codexceed.xmusic.service.ServiceManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class TopBar {
    private static final int CLOSE_SIZE = 14;

    private static class StatusInfo {
        final String text;
        final int color;
        final boolean animate;

        StatusInfo(String text, int color, boolean animate) {
            this.text = text;
            this.color = color;
            this.animate = animate;
        }
    }

    private StatusInfo getStatusInfo() {
        PlayerFacade facade = PlayerFacade.getInstance();
        PlayerState state = facade.snapshot();
        
        // 1. Error state check
        if (facade.getLastError() != null) {
            return new StatusInfo("Error: " + facade.getLastError(), 0xFFFF5555, false); // Red
        }

        // 2. YouTube native backend status check
        if ("youtube-native".equals(state.getBackendId())) {
            var yt = ServiceManager.getYouTube();
            if (yt != null) {
                var status = yt.getNativePlaybackStatus();
                if (status != null) {
                    switch (status) {
                        case SEARCHING:
                            return new StatusInfo("Searching YouTube...", GuiTheme.TEXT_MUTED, true);
                        case RESOLVING:
                            return new StatusInfo("Resolving Stream...", GuiTheme.ACCENT, true);
                        case DOWNLOADING:
                            return new StatusInfo("Downloading Stream...", GuiTheme.ACCENT, true);
                        case CONVERTING:
                            return new StatusInfo("Converting Audio...", GuiTheme.ACCENT, true);
                        case BUFFERING:
                            return new StatusInfo("Buffering Stream...", GuiTheme.ACCENT, true);
                        case ERROR:
                            return new StatusInfo("Playback Error", 0xFFFF5555, false);
                    }
                }
            }
        }

        // 3. LavaPlayer resolving check
        if ("lavaplayer".equals(state.getBackendId())) {
            var lp = ServiceManager.getLavaPlayerBackend();
            if (lp != null && lp.isResolving()) {
                return new StatusInfo("Loading Stream...", GuiTheme.ACCENT, true);
            }
        }

        // 4. Native AudioEngine checks
        var engine = AudioEngine.getInstance();
        if (engine.getState() == AudioEngine.State.LOADING) {
            return new StatusInfo("Loading file...", GuiTheme.ACCENT, true);
        }
        if (engine.isStalled()) {
            return new StatusInfo("Buffering...", GuiTheme.ACCENT, true);
        }

        // 5. Standard playback states
        if (state.isPlaying()) {
            return new StatusInfo("Playing", 0xFF55FF55, false); // Green
        }
        if (state.isPaused()) {
            return new StatusInfo("Paused", 0xFFFFBB33, false); // Amber/Yellow
        }

        return null;
    }

    public void render(GuiGraphics graphics, Font font, GuiFrame frame, int mouseX, int mouseY) {
        int x = frame.topBarX();
        int y = frame.topBarY();
        int w = frame.topBarWidth();
        int h = frame.topBarHeight();

        // Clean gradient panel
        GuiRender.mcPanelGradient(graphics, x, y, w, h);
        // Depth separator at bottom
        GuiRender.depthShadow(graphics, x + 1, y + h - 1, w - 2);

        // Mod name
        String modName = XMusic.MOD_NAME;
        int nameW = font.width(modName);
        GuiRender.shadowText(graphics, font, modName, x + 8, y + (h - 8) / 2, GuiTheme.ACCENT);

        // Accent underline
        int underY = y + h - 3;
        graphics.fill(x + 8, underY, x + 8 + nameW, underY + 1, GuiTheme.ACCENT_DARK);

        // Status indicator in the center
        StatusInfo info = getStatusInfo();
        if (info != null) {
            String text = info.text;
            if (font.width(text) > 180) {
                while (text.length() > 5 && font.width(text + "...") > 180) {
                    text = text.substring(0, text.length() - 1);
                }
                text = text + "...";
            }

            int textW = font.width(text);
            int dotSize = 4;
            int dotSpacing = 6;
            int totalW = dotSize + dotSpacing + textW;
            int startX = x + w / 2 - totalW / 2;
            int textY = y + (h - 8) / 2;
            int dotY = y + (h - dotSize) / 2;

            int dotColor = info.color;
            if (info.animate) {
                float pulse = (float) (Math.sin(System.currentTimeMillis() / 200.0) * 0.4 + 0.6);
                dotColor = AnimationHelper.withAlpha(info.color, pulse);
            }

            // Draw status dot
            graphics.fill(startX, dotY, startX + dotSize, dotY + dotSize, dotColor);
            // Draw status text
            GuiRender.shadowText(graphics, font, text, startX + dotSize + dotSpacing, textY, info.color);
        }

        // Close button
        int closeX = closeX(frame);
        int closeY = closeY(frame);
        boolean closeHover = GuiRender.inside(mouseX, mouseY, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE);
        float closeHoverLerp = HoverTracker.tick("topbar_close", closeHover);

        GuiRender.mcButtonSmooth(graphics, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE, closeHoverLerp, false);
        int closeColor = AnimationHelper.lerpColor(GuiTheme.TEXT_MUTED, GuiTheme.DANGER, closeHoverLerp);
        IconRenderer.clear(graphics, font, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE, closeColor);

        // Danger glow on hover
        if (closeHoverLerp > 0.01f) {
            int dangerGlow = AnimationHelper.withAlpha(GuiTheme.DANGER, closeHoverLerp * 0.15f);
            graphics.fill(closeX - 1, closeY - 1, closeX + CLOSE_SIZE + 1, closeY + CLOSE_SIZE + 1, dangerGlow);
        }

        if (closeHover) {
            GuiRender.tooltip(graphics, font, "Close", mouseX, mouseY, frame.x() + frame.width(), frame.y() + frame.height());
        }
    }

    public boolean closeClicked(GuiFrame frame, double mouseX, double mouseY) {
        return GuiRender.inside(mouseX, mouseY, closeX(frame), closeY(frame), CLOSE_SIZE, CLOSE_SIZE);
    }

    private int closeX(GuiFrame frame) {
        return frame.topBarX() + frame.topBarWidth() - CLOSE_SIZE - 5;
    }

    private int closeY(GuiFrame frame) {
        return frame.topBarY() + (frame.topBarHeight() - CLOSE_SIZE) / 2;
    }
}
