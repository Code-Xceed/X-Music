package com.codexceed.xmusic.gui.component;

import com.codexceed.xmusic.gui.render.GuiRender;
import com.codexceed.xmusic.gui.render.IconRenderer;
import com.codexceed.xmusic.gui.theme.GuiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Reusable toolbar button with auto-sized width based on text + icon.
 * Used across LibraryTab, DownloadsTab, and other views for consistent look.
 */
public final class ToolbarButton {

    private static final int ICON_SIZE = 10;
    private static final int ICON_TEXT_GAP = 4;
    private static final int PAD_H = 6; // horizontal padding inside button
    private static final int PAD_V = 2;

    /**
     * Render a toolbar button with icon + text, auto-sized to content.
     *
     * @return the width of the rendered button (for advancing cursor)
     */
    public static int render(GuiGraphics g, Font f, int x, int y, int h,
                             String text, IconRenderer.IconFunc icon,
                             boolean hovered, boolean active) {
        int textW = f.width(text);
        int totalW = PAD_H + ICON_SIZE + ICON_TEXT_GAP + textW + PAD_H;

        // Button background
        if (active) {
            g.fill(x, y, x + totalW, y + h, GuiTheme.PANEL_ACTIVE);
            GuiRender.accentGlow(g, x, y, totalW, h);
        } else {
            GuiRender.mcButton(g, x, y, totalW, h, hovered, false);
        }

        // Icon
        int iconX = x + PAD_H;
        int iconY = y + (h - ICON_SIZE) / 2;
        int iconColor = hovered ? GuiTheme.ACCENT : (active ? GuiTheme.ACCENT : GuiTheme.TEXT);
        icon.render(g, f, iconX, iconY, ICON_SIZE, ICON_SIZE, iconColor);

        // Text
        int textX = iconX + ICON_SIZE + ICON_TEXT_GAP;
        int textY = y + (h - 8) / 2;
        int textColor = hovered ? GuiTheme.ACCENT : (active ? GuiTheme.ACCENT : GuiTheme.TEXT);
        GuiRender.text(g, f, text, textX, textY, textColor);

        return totalW;
    }

    /**
     * Render an icon-only toolbar button (e.g. search toggle, folder open).
     *
     * @return the width of the rendered button
     */
    public static int renderIconOnly(GuiGraphics g, Font f, int x, int y, int h,
                                     IconRenderer.IconFunc icon,
                                     boolean hovered, boolean active) {
        int totalW = PAD_H + ICON_SIZE + PAD_H;

        if (active) {
            g.fill(x - 1, y - 1, x + totalW + 1, y + h + 1, GuiTheme.GLOW_ACCENT);
        }
        GuiRender.mcButton(g, x, y, totalW, h, hovered, false);

        int iconX = x + PAD_H;
        int iconY = y + (h - ICON_SIZE) / 2;
        int iconColor = active ? GuiTheme.ACCENT : (hovered ? GuiTheme.ACCENT : GuiTheme.TEXT);
        icon.render(g, f, iconX, iconY, ICON_SIZE, ICON_SIZE, iconColor);

        return totalW;
    }

    /**
     * Check if a button rendered at (x, y) with auto-sized width was clicked.
     * If font is null, approximates text width at ~6px per char.
     */
    public static boolean isClicked(Font f, String text, int x, int y, int h,
                                     double mx, double my) {
        int textW = f != null ? f.width(text) : text.length() * 6;
        int totalW = PAD_H + ICON_SIZE + ICON_TEXT_GAP + textW + PAD_H;
        return GuiRender.inside(mx, my, x, y, totalW, h);
    }

    /**
     * Check if an icon-only button was clicked.
     */
    public static boolean isIconClicked(int x, int y, int h,
                                         double mx, double my) {
        int totalW = PAD_H + ICON_SIZE + PAD_H;
        return GuiRender.inside(mx, my, x, y, totalW, h);
    }

    /**
     * Get the auto-sized width for a text button.
     * If font is null, approximates text width at ~6px per char.
     */
    public static int getWidth(Font f, String text) {
        int textW = f != null ? f.width(text) : text.length() * 6;
        return PAD_H + ICON_SIZE + ICON_TEXT_GAP + textW + PAD_H;
    }

    /**
     * Get the auto-sized width for an icon-only button.
     */
    public static int getIconWidth() {
        return PAD_H + ICON_SIZE + PAD_H;
    }

    /** Gap between consecutive toolbar buttons */
    public static final int GAP = 4;
}
