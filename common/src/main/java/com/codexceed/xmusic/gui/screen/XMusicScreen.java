package com.codexceed.xmusic.gui.screen;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.gui.GuiRoute;
import com.codexceed.xmusic.gui.component.ContentHost;
import com.codexceed.xmusic.gui.component.PlayerBar;
import com.codexceed.xmusic.gui.component.SidebarNav;
import com.codexceed.xmusic.gui.component.TopBar;
import com.codexceed.xmusic.gui.layout.GuiFrame;
import com.codexceed.xmusic.gui.render.GuiRender;
import com.codexceed.xmusic.gui.theme.GuiTheme;
import com.codexceed.xmusic.gui.util.AnimationHelper;
import com.codexceed.xmusic.service.ServiceManager;
import com.codexceed.xmusic.service.youtube.YouTubeToolManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class XMusicScreen extends Screen {
    private final TopBar topBar = new TopBar();
    private final SidebarNav sidebar = new SidebarNav();
    private final ContentHost content = new ContentHost();
    private final PlayerBar playerBar = new PlayerBar();

    private GuiRoute activeRoute = GuiRoute.HOME;

    // Intro/outro animation state
    private float introProgress = 0f;
    private boolean closing = false;
    private long openTimeMs = 0;

    public XMusicScreen() {
        super(Component.literal(XMusic.MOD_NAME));
        content.setRouteChanger(() -> activeRoute = GuiRoute.LIBRARY);
        openTimeMs = System.currentTimeMillis();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Animated overlay alpha
        float alpha = closing ? introProgress : AnimationHelper.easeInOut(introProgress);
        int overlayAlpha = (int) (0xDD * alpha);
        graphics.fill(0, 0, width, height, (overlayAlpha << 24) | (GuiTheme.OVERLAY & 0x00FFFFFF));
    }

    @Override
    protected void renderBlurredBackground() {
        // Keep the Minecraft world readable behind the custom modal.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Update intro/outro animation
        float delta = partialTick / 20f;
        if (closing) {
            introProgress = AnimationHelper.approach(introProgress, 0f, 10f, delta);
            if (introProgress < 0.01f) {
                minecraft.setScreen(null);
                return;
            }
        } else {
            introProgress = AnimationHelper.approach(introProgress, 1f, 8f, delta);
        }

        renderBackground(graphics, mouseX, mouseY, partialTick);

        GuiFrame frame = GuiFrame.calculate(width, height);

        // Animate the frame sliding in from top + scaling
        float eased = AnimationHelper.easeOut(introProgress);
        int slideOffset = (int) ((1f - eased) * -30f);
        float scale = 0.96f + 0.04f * eased;
        float alpha = eased;

        int fx = frame.x();
        int fy = frame.y() + slideOffset;
        int fw = frame.width();
        int fh = frame.height();

        // Apply alpha via overlay on the frame background
        int frameAlpha = (int) (0xFF * alpha);
        int frameColor = (frameAlpha << 24) | (GuiTheme.FRAME & 0x00FFFFFF);
        graphics.fill(fx, fy, fx + fw, fy + fh, frameColor);
        GuiRender.mcFrameBorder(graphics, fx, fy, fw, fh);

        // Render children with animated frame position offset
        // We use pose translate to shift all children by the slide offset
        graphics.pose().pushPose();
        graphics.pose().translate(0, slideOffset, 0);

        topBar.render(graphics, font, frame, mouseX, mouseY);
        sidebar.render(graphics, font, frame, activeRoute, mouseX, mouseY);
        content.render(graphics, font, frame, activeRoute, mouseX, mouseY);
        playerBar.render(graphics, font, frame, mouseX, mouseY);

        graphics.pose().popPose();

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        GuiFrame frame = GuiFrame.calculate(width, height);

        if (topBar.closeClicked(frame, mouseX, mouseY)) {
            closeAnimated();
            return true;
        }

        GuiRoute clickedRoute = sidebar.clicked(frame, mouseX, mouseY);
        if (clickedRoute != null) {
            activeRoute = clickedRoute;
            return true;
        }

        if (playerBar.mouseClicked(frame, mouseX, mouseY)) {
            return true;
        }

        if (content.mouseClicked(frame, activeRoute, mouseX, mouseY, button)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        GuiFrame frame = GuiFrame.calculate(width, height);
        if (playerBar.mouseReleased(frame, mouseX, mouseY)) {
            return true;
        }
        if (content.mouseReleased(frame, activeRoute, mouseX, mouseY)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        GuiFrame frame = GuiFrame.calculate(width, height);
        if (playerBar.mouseDragged(frame, mouseX, mouseY)) {
            return true;
        }
        if (content.mouseDragged(frame, activeRoute, mouseX, mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amountX, double amountY) {
        GuiFrame frame = GuiFrame.calculate(width, height);
        if (content.mouseScrolled(frame, activeRoute, mouseX, mouseY, amountY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amountX, amountY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (playerBar.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeAnimated();
            return true;
        }
        if (content.keyPressed(activeRoute, keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (playerBar.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (content.charTyped(activeRoute, codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Trigger animated close instead of instant close. */
    private void closeAnimated() {
        closing = true;
    }

    @Override
    public void onClose() {
        // If already closing animation, don't restart
        if (!closing) {
            closing = true;
        }
    }
}
