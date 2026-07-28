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
    private double scrollAmount = 0.0;

    public void render(GuiGraphicsExtractor graphics, Font font, GuiFrame frame, GuiRoute activeRoute, int mouseX, int mouseY) {
        int x = frame.sidebarX();
        int y = frame.sidebarY();
        int w = frame.sidebarWidth();
        int h = frame.sidebarHeight();

        // Clean panel
        GuiRender.mcPanelGradient(graphics, x, y, w, h);
        GuiRender.innerShadowTop(graphics, x + 1, y + 1, w - 2, 2);

        int totalHeight = GuiRoute.values().length * (ROW_H + ROW_GAP) - ROW_GAP + 10;
        double maxScroll = Math.max(0, totalHeight - h);
        scrollAmount = Math.max(0, Math.min(maxScroll, scrollAmount));

        graphics.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        int rowY = y + 5 - (int) scrollAmount;
        hoveredRoute = null;
        int activeRouteIndex = -1;
        int idx = 0;

        for (GuiRoute route : GuiRoute.values()) {
            boolean isHovered = GuiRender.inside(mouseX, mouseY, x + INSET, rowY, w - INSET * 2, ROW_H);
            if (isHovered) hoveredRoute = route.name();
            if (route == activeRoute) activeRouteIndex = idx;
            renderRoute(graphics, font, frame, route, activeRoute == route, x + INSET, rowY, w - INSET * 2, isHovered, mouseX, mouseY);
            rowY += ROW_H + ROW_GAP;
            idx++;
        }

        // Animated active indicator bar (relative to scrolled Y space)
        if (activeRouteIndex >= 0) {
            int targetRelativeY = activeRouteIndex * (ROW_H + ROW_GAP) + 5;
            if (activeIndicatorY < 0) {
                activeIndicatorY = targetRelativeY;
            } else {
                activeIndicatorY = AnimationHelper.approach(activeIndicatorY, targetRelativeY, 14f, 0.016f);
            }
            int indY = y + Math.round(activeIndicatorY) - (int) scrollAmount;
            // Accent bar (left edge, 2px)
            graphics.fill(x + INSET, indY + 3, x + INSET + 2, indY + ROW_H - 3, GuiTheme.ACCENT);
            // Soft glow
            int glowColor = AnimationHelper.withAlpha(GuiTheme.ACCENT, 0.10f);
            graphics.fill(x + INSET, indY + 1, x + INSET + 4, indY + ROW_H - 1, glowColor);
        }

        graphics.disableScissor();
    }

    public boolean mouseScrolled(GuiFrame frame, double mouseX, double mouseY, double amountY) {
        int x = frame.sidebarX();
        int y = frame.sidebarY();
        int w = frame.sidebarWidth();
        int h = frame.sidebarHeight();
        if (GuiRender.inside(mouseX, mouseY, x, y, w, h)) {
            int totalHeight = GuiRoute.values().length * (ROW_H + ROW_GAP) - ROW_GAP + 10;
            double maxScroll = Math.max(0, totalHeight - h);
            if (maxScroll > 0) {
                scrollAmount = Math.max(0, Math.min(maxScroll, scrollAmount - amountY * 12));
                return true;
            }
        }
        return false;
    }

    public GuiRoute clicked(GuiFrame frame, double mouseX, double mouseY) {
        if (!GuiRender.inside(mouseX, mouseY, frame.sidebarX(), frame.sidebarY(), frame.sidebarWidth(), frame.sidebarHeight())) {
            return null;
        }
        int x = frame.sidebarX() + INSET;
        int y = frame.sidebarY() + 5 - (int) scrollAmount;
        int w = frame.sidebarWidth() - INSET * 2;
        for (GuiRoute route : GuiRoute.values()) {
            if (GuiRender.inside(mouseX, mouseY, x, y, w, ROW_H)) {
                return route;
            }
            y += ROW_H + ROW_GAP;
        }
        return null;
    }

    private void renderRoute(GuiGraphicsExtractor graphics, Font font, GuiFrame frame, GuiRoute route, boolean active,
                             int x, int y, int width, boolean hovered, int mouseX, int mouseY) {
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
        int iconX = frame.compact() ? x + (width - ICON_SIZE) / 2 : x + ICON_PAD;
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

        // Label (only if not compact)
        if (!frame.compact()) {
            int labelX = iconX + ICON_SIZE + LABEL_GAP;
            int labelY = y + (ROW_H - 8) / 2;
            int labelColor = active ? GuiTheme.ACCENT
                    : AnimationHelper.lerpColor(GuiTheme.TEXT_MUTED, GuiTheme.TEXT, hoverLerp);
            GuiRender.shadowText(graphics, font, route.label(), labelX, labelY, labelColor);
        } else if (hovered) {
            // Show route label tooltip in compact mode
            GuiRender.tooltip(graphics, font, route.label(), mouseX, mouseY, frame.x() + frame.width(), frame.y() + frame.height());
        }
    }
}
