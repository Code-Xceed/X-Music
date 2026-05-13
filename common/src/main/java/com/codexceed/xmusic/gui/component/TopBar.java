package com.codexceed.xmusic.gui.component;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.gui.layout.GuiFrame;
import com.codexceed.xmusic.gui.render.GuiRender;
import com.codexceed.xmusic.gui.render.IconRenderer;
import com.codexceed.xmusic.gui.theme.GuiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class TopBar {
    private static final int CLOSE_SIZE = 16;

    public void render(GuiGraphics graphics, Font font, GuiFrame frame, int mouseX, int mouseY) {
        int x = frame.topBarX();
        int y = frame.topBarY();
        int w = frame.topBarWidth();
        int h = frame.topBarHeight();

        GuiRender.mcPanel(graphics, x, y, w, h);

        // Mod name with accent highlight
        String modName = XMusic.MOD_NAME;
        int nameW = font.width(modName);
        GuiRender.shadowText(graphics, font, modName, x + 10, y + 9, GuiTheme.ACCENT);
        // Accent underline
        graphics.fill(x + 10, y + h - 4, x + 10 + nameW, y + h - 3, GuiTheme.ACCENT);

        int closeX = closeX(frame);
        int closeY = closeY(frame);
        boolean closeHover = GuiRender.inside(mouseX, mouseY, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE);
        GuiRender.mcButton(graphics, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE, closeHover, false);
        IconRenderer.clear(graphics, font, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE, closeHover ? GuiTheme.DANGER : GuiTheme.TEXT_MUTED);

        // Tooltip
        if (closeHover) {
            GuiRender.tooltip(graphics, font, "Close", mouseX, mouseY, frame.x() + frame.width(), frame.y() + frame.height());
        }
    }

    public boolean closeClicked(GuiFrame frame, double mouseX, double mouseY) {
        return GuiRender.inside(mouseX, mouseY, closeX(frame), closeY(frame), CLOSE_SIZE, CLOSE_SIZE);
    }

    private int closeX(GuiFrame frame) {
        return frame.topBarX() + frame.topBarWidth() - CLOSE_SIZE - 6;
    }

    private int closeY(GuiFrame frame) {
        return frame.topBarY() + 6;
    }
}
