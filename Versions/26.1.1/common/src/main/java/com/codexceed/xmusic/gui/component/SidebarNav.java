package com.codexceed.xmusic.gui.component;

import com.codexceed.xmusic.gui.GuiRoute;
import com.codexceed.xmusic.gui.layout.GuiFrame;
import com.codexceed.xmusic.gui.render.GuiRender;
import com.codexceed.xmusic.gui.render.HoverTracker;
import com.codexceed.xmusic.gui.render.IconRenderer;
import com.codexceed.xmusic.gui.theme.GuiTheme;
import com.codexceed.xmusic.gui.util.AnimationHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class SidebarNav {
    private static final int ROW_H = 24;
    private static final int ROW_GAP = 2;
    private static final int ICON_SIZE = 14;
    private static final int ICON_PAD = 6;
    private static final int LABEL_GAP = 5;
    private static final int INSET = 3;

    private String hoveredRoute = null;
    private float activeIndicatorY = -1f;

    public void render(GuiGraphicsExtractor graphics, Font font, GuiFrame frame, GuiRoute activeRoute, int mouseX, int mouseY) {
        int x = frame.sidebarX();
        int y = frame.sidebarY();
        int w = frame.sidebarWidth();
        int h = frame.sidebarHeight();

        // Clean panel
        GuiRender.mcPanelGradient(graphics, x, y, w, h);
        GuiRender.innerShadowTop(graphics, x + 1, y + 1, w - 2, 2);

        graphics.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        int rowY = y + 5;
        hoveredRoute = null;
        int activeRowY = -1;

        for (GuiRoute route : GuiRoute.values()) {
            boolean isHovered = GuiRender.inside(mouseX, mouseY, x + INSET, rowY, w - INSET * 2, ROW_H);
            if (isHovered) hoveredRoute = route.name();
            if (route == activeRoute) activeRowY = rowY;
            renderRoute(graphics, font, route, activeRoute == route, x + INSET, rowY, w - INSET * 2, isHovered);
            rowY += ROW_H + ROW_GAP;
        }

        // Animated active indicator bar
        if (activeRowY >= 0) {
            if (activeIndicatorY < 0) {
                activeIndicatorY = activeRowY;
            } else {
                activeIndicatorY = AnimationHelper.approach(activeIndicatorY, activeRowY, 14f, 0.016f);
            }
            int indY = Math.round(activeIndicatorY);
            // Accent bar (left edge, 2px)
            graphics.fill(x + INSET, indY + 3, x + INSET + 2, indY + ROW_H - 3, GuiTheme.ACCENT);
            // Soft glow
            int glowColor = AnimationHelper.withAlpha(GuiTheme.ACCENT, 0.10f);
            graphics.fill(x + INSET, indY + 1, x + INSET + 4, indY + ROW_H - 1, glowColor);
        }

        graphics.disableScissor();
    }

    public GuiRoute clicked(GuiFrame frame, double mouseX, double mouseY) {
        int x = frame.sidebarX() + INSET;
        int y = frame.sidebarY() + 5;
        int w = frame.sidebarWidth() - INSET * 2;
        for (GuiRoute route : GuiRoute.values()) {
            if (GuiRender.inside(mouseX, mouseY, x, y, w, ROW_H)) {
                return route;
            }
            y += ROW_H + ROW_GAP;
        }
        return null;
    }

    private void renderRoute(GuiGraphicsExtractor graphics, Font font, GuiRoute route, boolean active,
                             int x, int y, int width, boolean hovered) {
        float hoverLerp = HoverTracker.tick("sidebar_" + route.name(), hovered);

        // Background (only on hover/active)
        if (active || hoverLerp > 0.01f) {
            GuiRender.mcButtonSmooth(graphics, x, y, width, ROW_H, hoverLerp, active);
        }

        // Hover glow
        if (!active && hoverLerp > 0.01f) {
            GuiRender.smoothHoverGlow(graphics, x, y, width, ROW_H, hoverLerp);
        }

        // Active glow
        if (active) {
            GuiRender.accentGlow(graphics, x, y, width, ROW_H);
        }

        // Icon
        int iconX = x + ICON_PAD;
        int iconY = y + (ROW_H - ICON_SIZE) / 2;
        int iconColor;
        if (active) {
            iconColor = GuiTheme.ACCENT;
        } else {
            iconColor = AnimationHelper.lerpColor(GuiTheme.TEXT_MUTED, GuiTheme.TEXT, hoverLerp);
        }

        switch (route) {
            case HOME:      IconRenderer.home(graphics, font, iconX, iconY, ICON_SIZE, ICON_SIZE, iconColor); break;
            case SEARCH:    IconRenderer.search(graphics, font, iconX, iconY, ICON_SIZE, ICON_SIZE, iconColor); break;
            case LIBRARY:   IconRenderer.library(graphics, font, iconX, iconY, ICON_SIZE, ICON_SIZE, iconColor); break;
            case DOWNLOADS: IconRenderer.downloads(graphics, font, iconX, iconY, ICON_SIZE, ICON_SIZE, iconColor); break;
            case SETTINGS:  IconRenderer.settings(graphics, font, iconX, iconY, ICON_SIZE, ICON_SIZE, iconColor); break;
        }

        // Label
        int labelX = iconX + ICON_SIZE + LABEL_GAP;
        int labelY = y + (ROW_H - 8) / 2;
        int labelColor = active ? GuiTheme.ACCENT
                : AnimationHelper.lerpColor(GuiTheme.TEXT_MUTED, GuiTheme.TEXT, hoverLerp);
        GuiRender.shadowText(graphics, font, route.label(), labelX, labelY, labelColor);
    }
}
