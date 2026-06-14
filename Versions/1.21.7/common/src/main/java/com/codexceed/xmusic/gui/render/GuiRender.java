package com.codexceed.xmusic.gui.render;

import com.codexceed.xmusic.gui.theme.GuiTheme;
import com.codexceed.xmusic.gui.util.AnimationHelper;
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

    public static void bevel(GuiGraphics g, int x, int y, int w, int h, boolean inset, float alpha) {
        int tl = inset ? GuiTheme.BEVEL_HIGHLIGHT_INSET : GuiTheme.BEVEL_HIGHLIGHT;
        int br = inset ? GuiTheme.BEVEL_SHADOW_INSET : GuiTheme.BEVEL_SHADOW;
        tl = AnimationHelper.withAlpha(tl, alpha);
        br = AnimationHelper.withAlpha(br, alpha);
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

    /** MC inset well with alpha fading. */
    public static void mcWell(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        int fill = AnimationHelper.withAlpha(GuiTheme.PANEL_DARK, alpha);
        g.fill(x, y, x + w, y + h, fill);
        bevel(g, x, y, w, h, true, alpha);
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

    /** Double-bevel frame border with alpha fading. */
    public static void mcFrameBorder(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        int highlight = AnimationHelper.withAlpha(GuiTheme.BEVEL_HIGHLIGHT, alpha);
        int shadow = AnimationHelper.withAlpha(GuiTheme.BEVEL_SHADOW, alpha);
        int edge = AnimationHelper.withAlpha(GuiTheme.FRAME_EDGE, alpha);
        // Outer raised bevel (2px)
        g.fill(x, y, x + w, y + 2, highlight);
        g.fill(x, y + 2, x + 2, y + h - 2, highlight);
        g.fill(x, y + h - 2, x + w, y + h, shadow);
        g.fill(x + w - 2, y + 2, x + w, y + h - 2, shadow);
        // Inner dark edge (1px)
        g.fill(x + 2, y + 2, x + w - 2, y + 3, edge);
        g.fill(x + 2, y + 3, x + 3, y + h - 3, edge);
        g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, edge);
        g.fill(x + w - 3, y + 3, x + w - 2, y + h - 3, edge);
    }

    // ── Premium Depth & Gradient Effects ─────────────────────────────────

    /**
     * Vertical gradient fill using multiple horizontal strips.
     * Creates a smooth gradient between two colors across the height.
     */
    public static void gradientV(GuiGraphics g, int x, int y, int w, int h, int colorTop, int colorBottom) {
        int steps = Math.min(h, 16); // 16-step gradient for efficiency
        if (steps <= 0) return;
        int stripH = h / steps;
        int remainder = h - stripH * steps;
        int cy = y;
        for (int i = 0; i < steps; i++) {
            float t = (float) i / (steps - 1);
            int color = AnimationHelper.lerpColor(colorTop, colorBottom, t);
            int sh = stripH + (i < remainder ? 1 : 0);
            g.fill(x, cy, x + w, cy + sh, color);
            cy += sh;
        }
    }

    /**
     * MC panel with vertical gradient fill for premium depth feel.
     */
    public static void mcPanelGradient(GuiGraphics g, int x, int y, int w, int h) {
        gradientV(g, x, y, w, h, GuiTheme.PANEL_GRAD_TOP, GuiTheme.PANEL_GRAD_BOTTOM);
        bevel(g, x, y, w, h, false);
    }

    /**
     * Inter-panel depth shadow: 2px dark strip on top edge to separate stacked panels.
     */
    public static void depthShadow(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, GuiTheme.DEPTH_SHADOW);
        g.fill(x, y + 1, x + w, y + 2, AnimationHelper.withAlpha(GuiTheme.DEPTH_SHADOW, 0.5f));
    }

    /**
     * Vignette effect: darkens edges of a rectangular area for visual depth.
     */
    public static void vignette(GuiGraphics g, int x, int y, int w, int h, int thickness) {
        for (int i = 0; i < thickness; i++) {
            float alpha = (1f - (float) i / thickness) * 0.12f;
            int color = AnimationHelper.withAlpha(0xFF000000, alpha);
            // top
            g.fill(x + i, y + i, x + w - i, y + i + 1, color);
            // bottom
            g.fill(x + i, y + h - i - 1, x + w - i, y + h - i, color);
            // left
            g.fill(x + i, y + i + 1, x + i + 1, y + h - i - 1, color);
            // right
            g.fill(x + w - i - 1, y + i + 1, x + w - i, y + h - i - 1, color);
        }
    }

    /**
     * Inner shadow gradient at the top of a panel (gives inset depth feel).
     */
    public static void innerShadowTop(GuiGraphics g, int x, int y, int w, int height) {
        for (int i = 0; i < height; i++) {
            float alpha = (1f - (float) i / height) * 0.2f;
            g.fill(x, y + i, x + w, y + i + 1, AnimationHelper.withAlpha(0xFF000000, alpha));
        }
    }

    // ── Premium Glow Effects ─────────────────────────────────────────────

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
        // Layer 4: deepest halo (4px)
        g.fill(x - 4, y - 4, x + w + 4, y - 2, GuiTheme.GLOW_ACCENT_DEEP);
        g.fill(x - 4, y + h + 2, x + w + 4, y + h + 4, GuiTheme.GLOW_ACCENT_DEEP);
        g.fill(x - 4, y - 2, x - 2, y + h + 2, GuiTheme.GLOW_ACCENT_DEEP);
        g.fill(x + w + 2, y - 2, x + w + 4, y + h + 2, GuiTheme.GLOW_ACCENT_DEEP);
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

    /**
     * Smooth hover glow: interpolated glow intensity based on hover factor (0→1).
     * Much smoother than the on/off glowRect — fades in and out with the hover.
     */
    public static void smoothHoverGlow(GuiGraphics g, int x, int y, int w, int h, float hoverFactor) {
        if (hoverFactor <= 0.01f) return;
        // Smooth background tint
        int bgTint = AnimationHelper.withAlpha(GuiTheme.HOVER_GLOW, hoverFactor);
        g.fill(x, y, x + w, y + h, bgTint);
        // Edge glow (fades with hover factor)
        int edgeGlow = AnimationHelper.withAlpha(GuiTheme.GLOW_ACCENT_SOFT, hoverFactor);
        g.fill(x - 1, y - 1, x + w + 1, y, edgeGlow);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, edgeGlow);
        g.fill(x - 1, y, x, y + h, edgeGlow);
        g.fill(x + w, y, x + w + 1, y + h, edgeGlow);
    }

    /**
     * Smooth button rendering with hover interpolation.
     * Provides silky-smooth transitions between normal/hover/active states.
     */
    public static void mcButtonSmooth(GuiGraphics g, int x, int y, int w, int h, float hoverFactor, boolean active) {
        int normalFill = active ? GuiTheme.PANEL_DARK : GuiTheme.PANEL;
        int hoverFill = active ? GuiTheme.PANEL_DARK : GuiTheme.PANEL_HOVER;
        int fill = AnimationHelper.lerpColor(normalFill, hoverFill, hoverFactor);
        g.fill(x, y, x + w, y + h, fill);

        // Bevel intensity follows hover
        int normalTl = active ? GuiTheme.BEVEL_HIGHLIGHT_INSET : GuiTheme.BEVEL_HIGHLIGHT;
        int hoverTl = active ? GuiTheme.BEVEL_HIGHLIGHT_INSET : GuiTheme.BEVEL_HIGHLIGHT_HOVER;
        int tl = AnimationHelper.lerpColor(normalTl, hoverTl, hoverFactor);
        int br = active ? GuiTheme.BEVEL_SHADOW_INSET : GuiTheme.BEVEL_SHADOW;

        g.fill(x, y, x + w, y + 1, tl);
        g.fill(x, y + 1, x + 1, y + h - 1, tl);
        g.fill(x, y + h - 1, x + w, y + h, br);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, br);

        // Subtle hover glow
        if (hoverFactor > 0.01f && !active) {
            smoothHoverGlow(g, x, y, w, h, hoverFactor * 0.5f);
        }
    }

    // ── MC-style tooltip ─────────────────────────────────────────────────

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

        // Subtle outer shadow for depth
        g.fill(tx + 1, ty + 1, tx + tw + 1, ty + th + 1, 0x30000000);

        g.fill(tx, ty, tx + tw, ty + th, GuiTheme.TOOLTIP_BG);
        bevel(g, tx, ty, tw, th, false);
        // Override bevel with tooltip border
        outline(g, tx, ty, tw, th, GuiTheme.TOOLTIP_BORDER);
        shadowText(g, font, text, tx + 4, ty + 2, GuiTheme.TEXT);
    }

    // ── Animated Slide Indicator ─────────────────────────────────────────

    /**
     * Renders a vertical accent indicator bar that slides smoothly.
     *
     * @param hoverFactor 0→1 for fade-in of the indicator
     */
    public static void slideIndicator(GuiGraphics g, int x, int y, int width, int height, float hoverFactor) {
        if (hoverFactor <= 0.01f) return;
        int indicatorH = (int) (height * 0.6f * hoverFactor);
        int indicatorY = y + (height - indicatorH) / 2;
        int color = AnimationHelper.withAlpha(GuiTheme.ACCENT, hoverFactor);
        g.fill(x, indicatorY, x + width, indicatorY + indicatorH, color);
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

    // ── Rounded rect primitives ──────────────────────────────────────────

    public static void fillRounded(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
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

    public static void fillRoundedGradient(GuiGraphics g, int x, int y, int w, int h, int r, int colorTop, int colorBottom) {
        if (r <= 0) { gradientV(g, x, y, w, h, colorTop, colorBottom); return; }
        r = Math.min(r, Math.min(w, h) / 2);
        for (int i = 0; i < h; i++) {
            float t = (float) i / (h - 1);
            int color = AnimationHelper.lerpColor(colorTop, colorBottom, t);
            int cy = y + i;
            int startX = x;
            int endX = x + w;
            if (i < r) {
                int dy = r - i;
                int dx = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
                startX = x + r - dx;
                endX = x + w - r + dx;
            } else if (i >= h - r) {
                int dy = i - (h - r - 1);
                int dx = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
                startX = x + r - dx;
                endX = x + w - r + dx;
            }
            g.fill(startX, cy, endX, cy + 1, color);
        }
    }

    public static void drawRoundedBorder(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
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

    public static boolean soundPlayedThisFrame = false;

    public static void playClickSound() {
        playClickSound(1.0f);
    }

    public static void playClickSound(float pitch) {
        soundPlayedThisFrame = true;
        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), pitch
            )
        );
    }

    public static void playTabSound() {
        soundPlayedThisFrame = true;
        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4f
            )
        );
    }

    public static void playActionSound() {
        soundPlayedThisFrame = true;
        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.2f
            )
        );
    }
}
