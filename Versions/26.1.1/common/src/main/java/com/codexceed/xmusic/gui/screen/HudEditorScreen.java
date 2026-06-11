package com.codexceed.xmusic.gui.screen;

import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.config.XMusicConfig;
import com.codexceed.xmusic.gui.render.GuiRender;
import com.codexceed.xmusic.gui.theme.GuiTheme;
import com.codexceed.xmusic.gui.util.AnimationHelper;
import com.codexceed.xmusic.hud.MiniPlayerOverlay;
import com.codexceed.xmusic.player.PlayerFacade;
import com.codexceed.xmusic.player.PlayerState;
import com.codexceed.xmusic.source.TrackRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * HUD editor â€” drag the mini-player to reposition.
 * Clean overlay with accent selection glow.
 */
public class HudEditorScreen extends Screen {

    private int dragHudX;
    private int dragHudY;
    private boolean dragging = false;
    private int dragOffsetX;
    private int dragOffsetY;

    private static final int HUD_W = 200;
    private static final int HUD_H = 36;
    private static final int RADIUS = 7;

    private long openTime;

    public HudEditorScreen() {
        super(Component.literal("HUD Editor"));
        openTime = System.currentTimeMillis();
        XMusicConfig cfg = ConfigManager.get();
        if (cfg.hudX >= 0 && cfg.hudY >= 0) {
            dragHudX = cfg.hudX;
            dragHudY = cfg.hudY;
        } else {
            int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
            switch (cfg.hudPosition) {
                case "TOP_LEFT":
                    dragHudX = 6; dragHudY = 6; break;
                case "TOP_RIGHT":
                    dragHudX = screenW - HUD_W - 6; dragHudY = 6; break;
                case "BOTTOM_LEFT":
                    dragHudX = 6; dragHudY = screenH - HUD_H - 6; break;
                case "BOTTOM_RIGHT":
                    dragHudX = screenW - HUD_W - 6; dragHudY = screenH - HUD_H - 6; break;
                case "TOP_CENTER":
                default:
                    dragHudX = (screenW - HUD_W) / 2; dragHudY = 6; break;
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x50000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        extractBackground(g, mouseX, mouseY, partialTick);
        Font font = Minecraft.getInstance().font;

        long elapsed = System.currentTimeMillis() - openTime;
        float intro = Math.min(1f, elapsed / 250f);

        // Selection glow
        float glowStr = dragging ? 0.5f : 0.25f;
        int glowAlpha = (int) (0x35 * glowStr * intro);
        fillRounded(g, dragHudX - 3, dragHudY - 3, HUD_W + 6, HUD_H + 6, RADIUS + 3,
                (glowAlpha << 24) | (GuiTheme.ACCENT & 0x00FFFFFF));

        // Selection border
        int borderAlpha = (int) (0x70 * intro);
        int borderColor = dragging ? GuiTheme.ACCENT : GuiTheme.TEXT_SOFT;
        drawRoundedBorder(g, dragHudX - 1, dragHudY - 1, HUD_W + 2, HUD_H + 2, RADIUS + 1,
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
        g.text(font, instr, instrX + 7, 6,
                (instrAlpha << 24) | (GuiTheme.TEXT_SOFT & 0x00FFFFFF), true);

        // Position
        String pos = dragHudX + ", " + dragHudY;
        int posW = font.width(pos) + 10;
        int posX = (this.width - posW) / 2;
        int posY = this.height - 16;
        fillRounded(g, posX, posY, posW, 12, 3,
                (int)(0xC0 * intro) << 24 | (GuiTheme.PANEL_DARK & 0x00FFFFFF));
        g.text(font, pos, posX + 5, posY + 2,
                (instrAlpha << 24) | (GuiTheme.TEXT_MUTED & 0x00FFFFFF), true);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void renderHudPreview(GuiGraphicsExtractor g, Font font, int x, int y) {
        PlayerState state = PlayerFacade.getInstance().snapshot();
        TrackRef track = state.getCurrentTrack();

        fillRounded(g, x, y, HUD_W, HUD_H, RADIUS, 0xE51E1E1E);
        drawRoundedBorder(g, x, y, HUD_W, HUD_H, RADIUS, 0x30505050);
        // Top highlight
        g.fill(x + RADIUS, y + 1, x + HUD_W - RADIUS, y + 2, 0x10FFFFFF);

        if (track != null) {
            GuiRender.truncated(g, font, track.getTitle(), x + 8, y + 5, HUD_W - 18, GuiTheme.TEXT);
            GuiRender.truncated(g, font, track.getArtist(), x + 8, y + 16, HUD_W - 40, GuiTheme.TEXT_SOFT);
            String icon = state.isPlaying() ? "\u25B6" : "\u275A\u275A";
            g.text(font, icon, x + HUD_W - 18, y + 5, GuiTheme.ACCENT, true);
        } else {
            GuiRender.shadowText(g, font, "No track playing", x + 8, y + HUD_H / 2 - 4, GuiTheme.TEXT_MUTED);
        }

        // Progress
        int barY = y + HUD_H - 5;
        g.fill(x + 6, barY, x + HUD_W - 6, barY + 2, 0x30505050);
        g.fill(x + 6, barY, x + 6 + (HUD_W - 12) / 3, barY + 2, 0x80000000 | (GuiTheme.ACCENT & 0x00FFFFFF));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean someBool) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (GuiRender.inside(mouseX, mouseY, dragHudX, dragHudY, HUD_W, HUD_H)) {
            dragging = true;
            dragOffsetX = (int) mouseX - dragHudX;
            dragOffsetY = (int) mouseY - dragHudY;
            return true;
        }
        return super.mouseClicked(event, someBool);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) { dragging = false; return true; }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (dragging) {
            dragHudX = Math.max(0, Math.min(this.width - HUD_W, (int) mouseX - dragOffsetX));
            dragHudY = Math.max(0, Math.min(this.height - HUD_H, (int) mouseY - dragOffsetY));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
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
                case "TOP_LEFT": dragHudX = 6; dragHudY = 6; break;
                case "TOP_RIGHT": dragHudX = this.width - HUD_W - 6; dragHudY = 6; break;
                case "BOTTOM_LEFT": dragHudX = 6; dragHudY = this.height - HUD_H - 6; break;
                case "BOTTOM_RIGHT": dragHudX = this.width - HUD_W - 6; dragHudY = this.height - HUD_H - 6; break;
                default: dragHudX = (this.width - HUD_W) / 2; dragHudY = 6; break;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // â”€â”€ Rounded rect primitives â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private static void fillRounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
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

    private static void drawRoundedBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
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
