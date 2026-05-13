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
 * HUD editor screen — transparent overlay where the user can drag
 * the mini-player HUD to any position on screen.
 */
public class HudEditorScreen extends Screen {

    private int dragHudX;
    private int dragHudY;
    private boolean dragging = false;
    private int dragOffsetX;
    private int dragOffsetY;

    private static final int HUD_W = 180;
    private static final int HUD_H = 38;

    public HudEditorScreen() {
        super(Component.literal("HUD Editor"));
        XMusicConfig cfg = ConfigManager.get();
        if (cfg.hudX >= 0 && cfg.hudY >= 0) {
            dragHudX = cfg.hudX;
            dragHudY = cfg.hudY;
        } else {
            // Compute from preset
            int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
            switch (cfg.hudPosition) {
                case "TOP_LEFT":
                    dragHudX = 6;
                    dragHudY = 6;
                    break;
                case "TOP_RIGHT":
                    dragHudX = screenW - HUD_W - 6;
                    dragHudY = 6;
                    break;
                case "BOTTOM_LEFT":
                    dragHudX = 6;
                    dragHudY = screenH - HUD_H - 6;
                    break;
                case "BOTTOM_RIGHT":
                    dragHudX = screenW - HUD_W - 6;
                    dragHudY = screenH - HUD_H - 6;
                    break;
                case "TOP_CENTER":
                default:
                    dragHudX = (screenW - HUD_W) / 2;
                    dragHudY = 6;
                    break;
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Semi-transparent dark overlay
        graphics.fill(0, 0, this.width, this.height, 0x80000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        Font font = Minecraft.getInstance().font;

        // Render the HUD preview at the drag position
        renderHudPreview(g, font, dragHudX, dragHudY);

        // Drag outline indicator
        int outlineColor = dragging ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED;
        GuiRender.outline(g, dragHudX - 1, dragHudY - 1, HUD_W + 2, HUD_H + 2, outlineColor);

        // Instructions
        String instr = "Drag the HUD to reposition. Press ESC to save.";
        GuiRender.shadowText(g, font, instr, (this.width - font.width(instr)) / 2, 8, GuiTheme.TEXT);

        // Reset button hint
        String reset = "Press R to reset to default position";
        GuiRender.shadowText(g, font, reset, (this.width - font.width(reset)) / 2, 20, GuiTheme.TEXT_MUTED);

        // Current position
        String pos = "X: " + dragHudX + "  Y: " + dragHudY;
        GuiRender.shadowText(g, font, pos, (this.width - font.width(pos)) / 2, this.height - 16, GuiTheme.TEXT_SOFT);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderHudPreview(GuiGraphics g, Font font, int x, int y) {
        // Render a preview of the HUD at the given position
        PlayerState state = PlayerFacade.getInstance().snapshot();
        TrackRef track = state.getCurrentTrack();

        // Background
        g.fill(x, y, x + HUD_W, y + HUD_H, GuiTheme.PANEL);
        GuiRender.bevel(g, x, y, HUD_W, HUD_H, false);

        if (track != null) {
            GuiRender.truncated(g, font, track.getTitle(), x + 5, y + 4, HUD_W - 30, GuiTheme.TEXT);
            GuiRender.truncated(g, font, track.getArtist(), x + 5, y + 14, HUD_W - 50, GuiTheme.TEXT_SOFT);
            String icon = state.isPlaying() ? "▶" : "❚❚";
            g.drawString(font, icon, x + HUD_W - 20, y + 4, GuiTheme.ACCENT, true);
        } else {
            GuiRender.shadowText(g, font, "No track playing", x + 5, y + 10, GuiTheme.TEXT_MUTED);
        }

        // Progress bar placeholder
        int barX = x + 5;
        int barY = y + HUD_H - 8;
        int barW = HUD_W - 10;
        g.fill(barX, barY, barX + barW, barY + 3, GuiTheme.PANEL_DARK);
        // Sample fill
        g.fill(barX, barY, barX + barW / 3, barY + 3, GuiTheme.ACCENT_DARK);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (GuiRender.inside(mouseX, mouseY, dragHudX, dragHudY, HUD_W, HUD_H)) {
            dragging = true;
            dragOffsetX = (int) mouseX - dragHudX;
            dragOffsetY = (int) mouseY - dragHudY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            dragHudX = (int) mouseX - dragOffsetX;
            dragHudY = (int) mouseY - dragOffsetY;
            // Clamp to screen
            dragHudX = Math.max(0, Math.min(this.width - HUD_W, dragHudX));
            dragHudY = Math.max(0, Math.min(this.height - HUD_H, dragHudY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            // Save position and close
            XMusicConfig cfg = ConfigManager.get();
            cfg.hudX = dragHudX;
            cfg.hudY = dragHudY;
            ConfigManager.save();
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
            // Reset to default
            dragHudX = -1;
            dragHudY = -1;
            // Re-compute default
            XMusicConfig cfg = ConfigManager.get();
            cfg.hudX = -1;
            cfg.hudY = -1;
            ConfigManager.save();
            // Re-compute visual position
            switch (cfg.hudPosition) {
                case "TOP_LEFT":
                    dragHudX = 6; dragHudY = 6; break;
                case "TOP_RIGHT":
                    dragHudX = this.width - HUD_W - 6; dragHudY = 6; break;
                case "BOTTOM_LEFT":
                    dragHudX = 6; dragHudY = this.height - HUD_H - 6; break;
                case "BOTTOM_RIGHT":
                    dragHudX = this.width - HUD_W - 6; dragHudY = this.height - HUD_H - 6; break;
                case "TOP_CENTER":
                default:
                    dragHudX = (this.width - HUD_W) / 2; dragHudY = 6; break;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
