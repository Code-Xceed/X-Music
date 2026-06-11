package com.codexceed.xmusic.gui.component;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.gui.layout.GuiFrame;
import com.codexceed.xmusic.gui.render.GuiRender;
import com.codexceed.xmusic.gui.render.HoverTracker;
import com.codexceed.xmusic.gui.render.IconRenderer;
import com.codexceed.xmusic.gui.theme.GuiTheme;
import com.codexceed.xmusic.gui.util.AnimationHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class TopBar {
    private static final int CLOSE_SIZE = 14;

    public void render(GuiGraphicsExtractor graphics, Font font, GuiFrame frame, int mouseX, int mouseY) {
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
