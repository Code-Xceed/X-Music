package com.codexceed.xmusic.gui.render;

import com.codexceed.xmusic.gui.theme.GuiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class GuiRender {
    private GuiRender() {}

    // ── Legacy flat panel (kept for compat) ──────────────────────────────

    public static void panel(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, fill);
        outline(g, x, y, w, h, border);
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    // ── MC Dark-Mode Bevel Primitives ────────────────────────────────────

    /** 1px bevel edges: raised (light top-left, dark bottom-right) or inset (reversed). */
    public static void bevel(GuiGraphics g, int x, int y, int w, int h, boolean inset) {
        int tl = inset ? GuiTheme.BEVEL_HIGHLIGHT_INSET : GuiTheme.BEVEL_HIGHLIGHT;
        int br = inset ? GuiTheme.BEVEL_SHADOW_INSET : GuiTheme.BEVEL_SHADOW;
        // top
        g.fill(x, y, x + w, y + 1, tl);
        // left
        g.fill(x, y + 1, x + 1, y + h - 1, tl);
        // bottom
        g.fill(x, y + h - 1, x + w, y + h, br);
        // right
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, br);
    }

    /** Bevel with hover-aware highlights. */
    public static void bevelHover(GuiGraphics g, int x, int y, int w, int h, boolean inset, boolean hovered) {
        int tl, br;
        if (inset) {
            tl = GuiTheme.BEVEL_HIGHLIGHT_INSET;
            br = GuiTheme.BEVEL_SHADOW_INSET;
        } else {
            tl = hovered ? GuiTheme.BEVEL_HIGHLIGHT_HOVER : GuiTheme.BEVEL_HIGHLIGHT;
            br = GuiTheme.BEVEL_SHADOW;
        }
        g.fill(x, y, x + w, y + 1, tl);
        g.fill(x, y + 1, x + 1, y + h - 1, tl);
        g.fill(x, y + h - 1, x + w, y + h, br);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, br);
    }

    /** MC raised panel: fill + raised bevel. */
    public static void mcPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, GuiTheme.PANEL);
        bevel(g, x, y, w, h, false);
    }

    /** MC button: raised bevel (normal/hover) or inset bevel (active/pressed). */
    public static void mcButton(GuiGraphics g, int x, int y, int w, int h, boolean hovered, boolean active) {
        int fill = active ? GuiTheme.PANEL_DARK : (hovered ? GuiTheme.PANEL_HOVER : GuiTheme.PANEL);
        g.fill(x, y, x + w, y + h, fill);
        bevelHover(g, x, y, w, h, active, hovered);
    }

    /** MC inset well: dark fill + inset bevel. For search bars, inputs. */
    public static void mcWell(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, GuiTheme.PANEL_DARK);
        bevel(g, x, y, w, h, true);
    }

    /** MC inventory slot: slot bg + inset bevel. */
    public static void mcSlot(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, GuiTheme.SLOT_BG);
        bevel(g, x, y, w, h, true);
    }

    /** Horizontal beveled separator line. */
    public static void mcSeparator(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, GuiTheme.BEVEL_HIGHLIGHT);
        g.fill(x, y + 1, x + w, y + 2, GuiTheme.BEVEL_SHADOW);
    }

    /** Double-bevel frame border: outer raised + inner dark line. */
    public static void mcFrameBorder(GuiGraphics g, int x, int y, int w, int h) {
        // Outer raised bevel (2px)
        g.fill(x, y, x + w, y + 2, GuiTheme.BEVEL_HIGHLIGHT);
        g.fill(x, y + 2, x + 2, y + h - 2, GuiTheme.BEVEL_HIGHLIGHT);
        g.fill(x, y + h - 2, x + w, y + h, GuiTheme.BEVEL_SHADOW);
        g.fill(x + w - 2, y + 2, x + w, y + h - 2, GuiTheme.BEVEL_SHADOW);
        // Inner dark edge (1px)
        g.fill(x + 2, y + 2, x + w - 2, y + 3, GuiTheme.FRAME_EDGE);
        g.fill(x + 2, y + 3, x + 3, y + h - 3, GuiTheme.FRAME_EDGE);
        g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, GuiTheme.FRAME_EDGE);
        g.fill(x + w - 3, y + 3, x + w - 2, y + h - 3, GuiTheme.FRAME_EDGE);
    }

    /** Accent glow: 2px semi-transparent accent halo around a rect. */
    public static void glowRect(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 2, y - 2, x + w + 2, y, GuiTheme.GLOW_ACCENT);       // top
        g.fill(x - 2, y + h, x + w + 2, y + h + 2, GuiTheme.GLOW_ACCENT); // bottom
        g.fill(x - 2, y, x, y + h, GuiTheme.GLOW_ACCENT);                 // left
        g.fill(x + w, y, x + w + 2, y + h, GuiTheme.GLOW_ACCENT);         // right
    }

    /** 1px accent outer border (for active/playing indicators). */
    public static void accentGlow(GuiGraphics g, int x, int y, int w, int h) {
        // Premium multi-layer glow: outer soft → mid → inner bright
        // Layer 3: outermost soft halo (3px)
        g.fill(x - 3, y - 3, x + w + 3, y - 1, GuiTheme.GLOW_ACCENT_SOFT);
        g.fill(x - 3, y + h + 1, x + w + 3, y + h + 3, GuiTheme.GLOW_ACCENT_SOFT);
        g.fill(x - 3, y - 1, x - 1, y + h + 1, GuiTheme.GLOW_ACCENT_SOFT);
        g.fill(x + w + 1, y - 1, x + w + 3, y + h + 1, GuiTheme.GLOW_ACCENT_SOFT);
        // Layer 2: mid glow (2px)
        g.fill(x - 2, y - 2, x + w + 2, y, GuiTheme.GLOW_ACCENT_MID);
        g.fill(x - 2, y + h, x + w + 2, y + h + 2, GuiTheme.GLOW_ACCENT_MID);
        g.fill(x - 2, y, x, y + h, GuiTheme.GLOW_ACCENT_MID);
        g.fill(x + w, y, x + w + 2, y + h, GuiTheme.GLOW_ACCENT_MID);
        // Layer 1: inner bright glow (1px)
        g.fill(x - 1, y - 1, x + w + 1, y, GuiTheme.GLOW_ACCENT);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, GuiTheme.GLOW_ACCENT);
        g.fill(x - 1, y, x, y + h, GuiTheme.GLOW_ACCENT);
        g.fill(x + w, y, x + w + 1, y + h, GuiTheme.GLOW_ACCENT);
    }

    /** MC-style tooltip: dark bg + bevel border + shadow text, positioned near cursor. */
    public static void tooltip(GuiGraphics g, Font font, String text, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        int tw = font.width(text) + 8;
        int th = 12;
        int tx = mouseX + 8;
        int ty = mouseY - th - 4;
        // Clamp to screen
        if (tx + tw > screenWidth - 4) tx = mouseX - tw - 4;
        if (ty < 4) ty = mouseY + 12;
        if (ty + th > screenHeight - 4) ty = screenHeight - th - 4;

        g.fill(tx, ty, tx + tw, ty + th, GuiTheme.TOOLTIP_BG);
        bevel(g, tx, ty, tw, th, false);
        // Override bevel with tooltip border
        outline(g, tx, ty, tw, th, GuiTheme.TOOLTIP_BORDER);
        shadowText(g, font, text, tx + 4, ty + 2, GuiTheme.TEXT);
    }

    // ── Text ──────────────────────────────────────────────────────────────

    public static void text(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    public static void shadowText(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, true);
    }

    public static void centeredText(GuiGraphics g, Font font, String text, int centerX, int y, int color) {
        g.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    public static void truncated(GuiGraphics g, Font font, String text, int x, int y, int maxWidth, int color) {
        if (text == null) {
            text = "";
        }
        String value = text;
        if (font.width(value) > maxWidth) {
            value = font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("..."))) + "...";
        }
        g.drawString(font, value, x, y, color, false);
    }

    // ── Hit-test ──────────────────────────────────────────────────────────

    public static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
