package com.codexceed.xmusic.gui.component;

import com.codexceed.xmusic.gui.GuiRoute;
import com.codexceed.xmusic.gui.layout.GuiFrame;
import com.codexceed.xmusic.gui.render.GuiRender;
import com.codexceed.xmusic.gui.render.HoverTracker;
import com.codexceed.xmusic.gui.render.IconRenderer;
import com.codexceed.xmusic.gui.theme.GuiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class SidebarNav {
    private static final int ROW_H = 28;
    private static final int ROW_GAP = 3;
    private static final int ICON_SIZE = 16;
    private static final int ICON_PAD = 5;
    private static final int LABEL_GAP = 6;

    private String hoveredRoute = null;

    public void render(GuiGraphics graphics, Font font, GuiFrame frame, GuiRoute activeRoute, int mouseX, int mouseY) {
        int x = frame.sidebarX();
        int y = frame.sidebarY();
        int w = frame.sidebarWidth();
        int h = frame.sidebarHeight();
        GuiRender.mcPanel(graphics, x, y, w, h);

        graphics.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        int rowY = y + 6;
        hoveredRoute = null;
        for (GuiRoute route : GuiRoute.values()) {
            boolean isHovered = GuiRender.inside(mouseX, mouseY, x + 3, rowY, w - 6, ROW_H);
            if (isHovered) hoveredRoute = route.name();
            renderRoute(graphics, font, route, activeRoute == route, x + 3, rowY, w - 6, isHovered, mouseX, mouseY);
            rowY += ROW_H + ROW_GAP;
        }
        graphics.disableScissor();
    }

    public GuiRoute clicked(GuiFrame frame, double mouseX, double mouseY) {
        int x = frame.sidebarX() + 3;
        int y = frame.sidebarY() + 6;
        int w = frame.sidebarWidth() - 6;
        for (GuiRoute route : GuiRoute.values()) {
            if (GuiRender.inside(mouseX, mouseY, x, y, w, ROW_H)) {
                return route;
            }
            y += ROW_H + ROW_GAP;
        }
        return null;
    }

    private void renderRoute(GuiGraphics graphics, Font font, GuiRoute route, boolean active,
                             int x, int y, int width, boolean hovered, int mouseX, int mouseY) {
        // Hover animation lerp
        float hoverLerp = HoverTracker.tick("sidebar_" + route.name(), hovered);

        // MC-style button: raised on hover, inset when active
        if (active || hovered) {
            GuiRender.mcButton(graphics, x, y, width, ROW_H, hovered, active);
        }

        // Active indicator: left accent bar + glow
        if (active) {
            graphics.fill(x, y, x + 3, y + ROW_H, GuiTheme.ACCENT);
            GuiRender.accentGlow(graphics, x, y, width, ROW_H);
        } else if (hoverLerp > 0) {
            // Subtle hover glow
            GuiRender.glowRect(graphics, x, y, width, ROW_H);
        }

        // Icon on left side
        int iconX = x + ICON_PAD;
        int iconY = y + (ROW_H - ICON_SIZE) / 2;
        int iconColor = active ? GuiTheme.ACCENT : (hoverLerp > 0.5f ? GuiTheme.TEXT : GuiTheme.TEXT_MUTED);

        switch (route) {
            case HOME:      IconRenderer.home(graphics, font, iconX, iconY, ICON_SIZE, ICON_SIZE, iconColor); break;
            case SEARCH:    IconRenderer.search(graphics, font, iconX, iconY, ICON_SIZE, ICON_SIZE, iconColor); break;
            case LIBRARY:   IconRenderer.library(graphics, font, iconX, iconY, ICON_SIZE, ICON_SIZE, iconColor); break;
            case DOWNLOADS: IconRenderer.downloads(graphics, font, iconX, iconY, ICON_SIZE, ICON_SIZE, iconColor); break;
            case SETTINGS:  IconRenderer.settings(graphics, font, iconX, iconY, ICON_SIZE, ICON_SIZE, iconColor); break;
        }

        // Label text beside icon
        int labelX = iconX + ICON_SIZE + LABEL_GAP;
        int labelY = y + (ROW_H - 8) / 2;
        int labelColor = active ? GuiTheme.ACCENT : (hoverLerp > 0.5f ? GuiTheme.TEXT : GuiTheme.TEXT_MUTED);
        GuiRender.shadowText(graphics, font, route.label(), labelX, labelY, labelColor);
    }
}
