package com.codexceed.xmusic.gui.component;

import com.codexceed.xmusic.download.DownloadManager;
import com.codexceed.xmusic.download.DownloadState;
import com.codexceed.xmusic.gui.render.GuiRender;
import com.codexceed.xmusic.gui.render.HoverTracker;
import com.codexceed.xmusic.gui.render.IconRenderer;
import com.codexceed.xmusic.gui.theme.GuiTheme;
import com.codexceed.xmusic.source.TrackRef;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class TrackRow {
    public static final int HEIGHT = 26;
    private static final int BTN_SIZE = 12;
    private static final int BTN_PAD = 2;
    private static final int BTN_STEP = BTN_SIZE + BTN_PAD * 2; // 16
    private static final int BTN_GAP = 4; // breathing space between heart and download
    private static final int BUTTONS_W = BTN_STEP * 2 + BTN_GAP; // 36
    private static final int RIGHT_MARGIN = 4; // margin from row edge to buttons

    public void render(GuiGraphics graphics, Font font, int x, int y, int width,
                       String title, String subtitle, String meta, boolean isPlaying, boolean isSelected,
                       int mouseX, int mouseY, int screenW, int screenH) {
        render(graphics, font, x, y, width, title, subtitle, meta, isPlaying, isSelected, false, mouseX, mouseY, screenW, screenH);
    }

    public void render(GuiGraphics graphics, Font font, int x, int y, int width,
                       String title, String subtitle, String meta, boolean isPlaying, boolean isSelected, boolean isFavorite,
                       int mouseX, int mouseY, int screenW, int screenH) {
        render(graphics, font, x, y, width, title, subtitle, meta, isPlaying, isSelected, isFavorite, null, mouseX, mouseY, screenW, screenH);
    }

    public void render(GuiGraphics graphics, Font font, int x, int y, int width,
                       String title, String subtitle, String meta, boolean isPlaying, boolean isSelected, boolean isFavorite, TrackRef trackRef,
                       int mouseX, int mouseY, int screenW, int screenH) {
        // Background: selected > playing > default
        int fill;
        if (isSelected) {
            fill = GuiTheme.PANEL_ACTIVE;
        } else if (isPlaying) {
            fill = 0xFF1A1520; // subtle dark tint for now-playing
        } else {
            fill = GuiTheme.PANEL_DARK;
        }
        graphics.fill(x, y, x + width, y + HEIGHT, fill);
        // No hard outline border — use premium glow for selected/playing
        if (isSelected) {
            GuiRender.accentGlow(graphics, x, y, width, HEIGHT);
        } else if (isPlaying) {
            GuiRender.accentGlow(graphics, x, y, width, HEIGHT);
        }

        // Subtle depth bevel for non-playing rows
        if (!isPlaying && !isSelected) {
            GuiRender.bevel(graphics, x, y, width, HEIGHT, false);
        }

        int iconX = x + 5;
        int iconY = y + (HEIGHT - 14) / 2;
        // Icon area: MC inventory slot style
        GuiRender.mcSlot(graphics, iconX, iconY, 14, 14);
        // Animate noteblock like vanilla MC: always noteblock, glow when playing
        int iconColor = isPlaying ? GuiTheme.ACCENT : (isSelected ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);
        if (isPlaying) {
            // Glow in icon frame when playing
            GuiRender.accentGlow(graphics, iconX, iconY, 14, 14);
            IconRenderer.musicNote(graphics, font, iconX, iconY, 14, 14, iconColor);
        } else {
            IconRenderer.musicNote(graphics, font, iconX, iconY, 14, 14, iconColor);
        }

        int textX = x + 24;
        int metaW = Math.min(52, Math.max(34, width / 5));

        // Buttons — far right with gap + right margin so they don't overflow
        int heartX = x + width - BUTTONS_W - RIGHT_MARGIN;
        int downX = heartX + BTN_STEP + BTN_GAP;
        int metaX = heartX - metaW - 6; // 6px gap between meta and buttons

        int titleColor = isPlaying ? GuiTheme.ACCENT : (isSelected ? GuiTheme.TEXT : GuiTheme.TEXT_SOFT);
        int textW = metaX - textX;
        GuiRender.truncated(graphics, font, title, textX, y + 4, textW, titleColor);
        GuiRender.truncated(graphics, font, subtitle, textX, y + 15, textW, GuiTheme.TEXT_MUTED);
        GuiRender.truncated(graphics, font, meta, metaX, y + 9, metaW, GuiTheme.TEXT_MUTED);

        // Heart icon: MC button style with hover + favorite state
        int heartBtnY = y + (HEIGHT - BTN_SIZE) / 2;
        boolean heartHover = GuiRender.inside(mouseX, mouseY, heartX + BTN_PAD, heartBtnY, BTN_SIZE, BTN_SIZE);
        float heartLerp = HoverTracker.tick("tr_heart_" + x + "_" + y, heartHover);
        GuiRender.mcButton(graphics, heartX, heartBtnY - BTN_PAD, BTN_STEP, BTN_STEP, heartHover, false);
        if (heartLerp > 0) GuiRender.glowRect(graphics, heartX, heartBtnY - BTN_PAD, BTN_STEP, BTN_STEP);
        if (isFavorite) {
            // Filled red heart with glow for active state
            GuiRender.accentGlow(graphics, heartX, heartBtnY - BTN_PAD, BTN_STEP, BTN_STEP);
            IconRenderer.heartFilled(graphics, font, heartX + BTN_PAD, heartBtnY, BTN_SIZE, BTN_SIZE, GuiTheme.DANGER);
        } else {
            int heartColor = heartHover ? GuiTheme.DANGER : GuiTheme.TEXT_MUTED;
            IconRenderer.heart(graphics, font, heartX + BTN_PAD, heartBtnY, BTN_SIZE, BTN_SIZE, heartColor);
        }

        // Download icon: dynamic state (default / downloading / completed / failed)
        int downBtnY = y + (HEIGHT - BTN_SIZE) / 2;
        boolean downHover = GuiRender.inside(mouseX, mouseY, downX + BTN_PAD, downBtnY, BTN_SIZE, BTN_SIZE);
        DownloadState dlState = trackRef != null ? DownloadManager.getInstance().getState(trackRef) : DownloadState.NONE;
        float dlProgress = trackRef != null ? DownloadManager.getInstance().getProgress(trackRef) : 0f;

        GuiRender.mcButton(graphics, downX, downBtnY - BTN_PAD, BTN_STEP, BTN_STEP, downHover, dlState == DownloadState.COMPLETED);
        float downLerp = HoverTracker.tick("tr_down_" + x + "_" + y, downHover);
        if (downLerp > 0) GuiRender.glowRect(graphics, downX, downBtnY - BTN_PAD, BTN_STEP, BTN_STEP);

        if (dlState == DownloadState.DOWNLOADING) {
            // Pulsing download icon with glow + mini progress bar
            float pulse = (float)(Math.sin(System.currentTimeMillis() / 300.0) * 0.4 + 0.6);
            int pulseColor = ((int)(0x60 + 0x60 * pulse) << 24) | (GuiTheme.ACCENT & 0x00FFFFFF);
            graphics.fill(downX, downBtnY - BTN_PAD, downX + BTN_STEP, downBtnY - BTN_PAD + BTN_STEP, pulseColor);
            IconRenderer.download(graphics, font, downX + BTN_PAD, downBtnY, BTN_SIZE, BTN_SIZE, GuiTheme.ACCENT);
            // Mini progress bar at bottom of button
            int pBarY = downBtnY + BTN_SIZE - 2;
            int pBarW = (int)(BTN_SIZE * dlProgress);
            graphics.fill(downX + BTN_PAD, pBarY, downX + BTN_PAD + pBarW, pBarY + 2, GuiTheme.ACCENT);
        } else if (dlState == DownloadState.COMPLETED) {
            // Checkmark icon for downloaded state
            GuiRender.accentGlow(graphics, downX, downBtnY - BTN_PAD, BTN_STEP, BTN_STEP);
            IconRenderer.checkmark(graphics, font, downX + BTN_PAD, downBtnY, BTN_SIZE, BTN_SIZE, GuiTheme.ACCENT);
        } else if (dlState == DownloadState.FAILED) {
            IconRenderer.download(graphics, font, downX + BTN_PAD, downBtnY, BTN_SIZE, BTN_SIZE, GuiTheme.DANGER);
        } else {
            // Default: download arrow
            IconRenderer.download(graphics, font, downX + BTN_PAD, downBtnY, BTN_SIZE, BTN_SIZE, downHover ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);
        }

        // Tooltips
        if (heartHover) {
            GuiRender.tooltip(graphics, font, isFavorite ? "Unlike" : "Like", mouseX, mouseY, screenW, screenH);
        } else if (downHover) {
            String tip;
            if (dlState == DownloadState.DOWNLOADING) tip = "Downloading... " + (int)(dlProgress * 100) + "%";
            else if (dlState == DownloadState.COMPLETED) tip = "Downloaded";
            else if (dlState == DownloadState.FAILED) tip = "Download failed — retry?";
            else tip = "Download";
            GuiRender.tooltip(graphics, font, tip, mouseX, mouseY, screenW, screenH);
        }
    }

    /** Legacy render without hover/tooltip (for backward compat). */
    public void render(GuiGraphics graphics, Font font, int x, int y, int width,
                       String title, String subtitle, String meta, boolean isPlaying, boolean isSelected) {
        render(graphics, font, x, y, width, title, subtitle, meta, isPlaying, isSelected, false, -1, -1, 9999, 9999);
    }

    /**
     * Check if the heart button was clicked at the given mouse position.
     * Returns true if the click is within the heart button bounds.
     */
    public static boolean isHeartClicked(int rowX, int rowY, int rowWidth, double mouseX, double mouseY) {
        int heartX = rowX + rowWidth - BUTTONS_W - RIGHT_MARGIN;
        int heartBtnY = rowY + (HEIGHT - BTN_SIZE) / 2;
        return GuiRender.inside(mouseX, mouseY, heartX, heartBtnY - BTN_PAD, BTN_STEP, BTN_STEP);
    }

    /**
     * Check if the download button was clicked at the given mouse position.
     */
    public static boolean isDownloadClicked(int rowX, int rowY, int rowWidth, double mouseX, double mouseY) {
        int downX = rowX + rowWidth - BUTTONS_W - RIGHT_MARGIN + BTN_STEP + BTN_GAP;
        int downBtnY = rowY + (HEIGHT - BTN_SIZE) / 2;
        return GuiRender.inside(mouseX, mouseY, downX, downBtnY - BTN_PAD, BTN_STEP, BTN_STEP);
    }
}
