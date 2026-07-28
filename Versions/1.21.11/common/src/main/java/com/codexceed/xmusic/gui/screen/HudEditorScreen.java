package com.codexceed.xmusic.gui.screen;

import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.config.XMusicConfig;
import com.codexceed.xmusic.gui.render.GuiRender;
import com.codexceed.xmusic.gui.theme.GuiTheme;
import com.codexceed.xmusic.hud.MiniPlayerOverlay;
import com.codexceed.xmusic.player.PlayerFacade;
import com.codexceed.xmusic.player.PlayerState;
import com.codexceed.xmusic.source.TrackRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * HUD editor — drag the mini-player to reposition.
 * Clean overlay with accent selection glow.
 */
public class HudEditorScreen extends Screen {

    private int dragHudX;
    private int dragHudY;
    private boolean dragging = false;
    private int dragOffsetX;
    private int dragOffsetY;

    private int getHudW() {
        return com.codexceed.xmusic.hud.HudRenderer.getInstance().getMiniPlayer().getHudWidth();
    }

    private int getHudH() {
        return com.codexceed.xmusic.hud.HudRenderer.getInstance().getMiniPlayer().getHudHeight();
    }

    private int getRadius() {
        return 0; // Corners are sharp with mcFrameBorder
    }

    private long openTime;

    public HudEditorScreen() {
        super(Component.literal("HUD Editor"));
        openTime = System.currentTimeMillis();
        XMusicConfig cfg = ConfigManager.get();
        int hudW = getHudW();
        int hudH = getHudH();
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        if (cfg.hudX >= 0 && cfg.hudY >= 0) {
            dragHudX = Math.max(0, Math.min(screenW - hudW, cfg.hudX));
            dragHudY = Math.max(0, Math.min(screenH - hudH, cfg.hudY));
        } else {
            switch (cfg.hudPosition) {
                case "TOP_LEFT": dragHudX = 8; dragHudY = 8; break;
                case "TOP_RIGHT": dragHudX = screenW - hudW - 8; dragHudY = 8; break;
                case "BOTTOM_LEFT": dragHudX = 8; dragHudY = screenH - hudH - 8; break;
                case "BOTTOM_RIGHT": dragHudX = screenW - hudW - 8; dragHudY = screenH - hudH - 8; break;
                default: dragHudX = (screenW - hudW) / 2; dragHudY = 8; break;
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x50000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        Font font = Minecraft.getInstance().font;

        long elapsed = System.currentTimeMillis() - openTime;
        float intro = Math.min(1f, elapsed / 250f);

        // Selection glow
        float glowStr = dragging ? 0.5f : 0.25f;
        int glowAlpha = (int) (0x35 * glowStr * intro);
        fillRounded(g, dragHudX - 3, dragHudY - 3, getHudW() + 6, getHudH() + 6, getRadius() + 3,
                (glowAlpha << 24) | (GuiTheme.ACCENT & 0x00FFFFFF));

        // Selection border
        int borderAlpha = (int) (0x70 * intro);
        int borderColor = dragging ? GuiTheme.ACCENT : GuiTheme.TEXT_SOFT;
        drawRoundedBorder(g, dragHudX - 1, dragHudY - 1, getHudW() + 2, getHudH() + 2, getRadius() + 1,
                (borderAlpha << 24) | (borderColor & 0x00FFFFFF));

        // HUD preview
        renderHudPreview(g, font, dragHudX, dragHudY);

        // Instructions
        int instrAlpha = (int) (0xFF * intro);
        String instr = "Drag to reposition  \u00B7  ESC to save  \u00B7  R to reset";
        int instrW = font.width(instr) + 14;
        int instrX = (this.width - instrW) / 2;
        fillRounded(g, instrX, 3, instrW, 14, 3,
                (int)(0xC0 * intro) << 24 | (GuiTheme.PANEL_DARK & 0x00FFFFFF));
        g.drawString(font, instr, instrX + 7, 6,
                (instrAlpha << 24) | (GuiTheme.TEXT_SOFT & 0x00FFFFFF), true);

        // Position
        String pos = dragHudX + ", " + dragHudY;
        int posW = font.width(pos) + 10;
        int posX = (this.width - posW) / 2;
        int posY = this.height - 16;
        fillRounded(g, posX, posY, posW, 12, 3,
                (int)(0xC0 * intro) << 24 | (GuiTheme.PANEL_DARK & 0x00FFFFFF));
        g.drawString(font, pos, posX + 5, posY + 2,
                (instrAlpha << 24) | (GuiTheme.TEXT_MUTED & 0x00FFFFFF), true);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderHudPreview(GuiGraphics g, Font font, int x, int y) {
        PlayerState state = PlayerFacade.getInstance().snapshot();
        TrackRef track = state.getCurrentTrack();
        com.codexceed.xmusic.hud.HudRenderer.getInstance().getMiniPlayer().renderCompactHud(g, font, x, y, state, track, 1.0f);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean isHandled) {
        double mouseX = event.x(); double mouseY = event.y(); int button = event.button();
        if (GuiRender.inside(mouseX, mouseY, dragHudX, dragHudY, getHudW(), getHudH())) {
            dragging = true;
            dragOffsetX = (int) mouseX - dragHudX;
            dragOffsetY = (int) mouseY - dragHudY;
            return true;
        }
        return super.mouseClicked(event, isHandled);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double mouseX = event.x(); double mouseY = event.y(); int button = event.button();
        if (dragging) { dragging = false; return true; }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x(); double mouseY = event.y(); int button = event.button();
        if (dragging) {
            dragHudX = Math.max(0, Math.min(this.width - getHudW(), (int) mouseX - dragOffsetX));
            dragHudY = Math.max(0, Math.min(this.height - getHudH(), (int) mouseY - dragOffsetY));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key(); int scanCode = event.scancode(); int modifiers = event.modifiers();
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            XMusicConfig cfg = ConfigManager.get();
            cfg.hudX = dragHudX;
            cfg.hudY = dragHudY;
            ConfigManager.save();
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
            XMusicConfig cfg = ConfigManager.get();
            cfg.hudX = -1; cfg.hudY = -1;
            ConfigManager.save();
            switch (cfg.hudPosition) {
                case "TOP_LEFT": dragHudX = 8; dragHudY = 8; break;
                case "TOP_RIGHT": dragHudX = this.width - getHudW() - 8; dragHudY = 8; break;
                case "BOTTOM_LEFT": dragHudX = 8; dragHudY = this.height - getHudH() - 8; break;
                case "BOTTOM_RIGHT": dragHudX = this.width - getHudW() - 8; dragHudY = this.height - getHudH() - 8; break;
                default: dragHudX = (this.width - getHudW()) / 2; dragHudY = 8; break;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Rounded rect primitives ──────────────────────────────────────────

    private static void fillRounded(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
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

    private static void drawRoundedBorder(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
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
            int dxI = (int) Math.sqrt(Math.max(0, (r - 1) * (r - 1) - dy * dy));
            for (int px = r - dx; px < r - dxI; px++) g.fill(x + px, y + i, x + px + 1, y + i + 1, color);
            for (int px = w - r + dxI; px < w - r + dx; px++) g.fill(x + px, y + i, x + px + 1, y + i + 1, color);
            for (int px = r - dx; px < r - dxI; px++) g.fill(x + px, y + h - i - 1, x + px + 1, y + h - i, color);
            for (int px = w - r + dxI; px < w - r + dx; px++) g.fill(x + px, y + h - i - 1, x + px + 1, y + h - i, color);
        }
    }
}

