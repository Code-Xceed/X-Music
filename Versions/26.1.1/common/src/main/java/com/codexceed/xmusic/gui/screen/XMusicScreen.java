package com.codexceed.xmusic.gui.screen;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.config.XMusicConfig;
import com.codexceed.xmusic.gui.GuiRoute;
import com.codexceed.xmusic.gui.component.ContentHost;
import com.codexceed.xmusic.gui.component.PlayerBar;
import com.codexceed.xmusic.gui.component.SidebarNav;
import com.codexceed.xmusic.gui.component.TopBar;
import com.codexceed.xmusic.gui.layout.GuiFrame;
import com.codexceed.xmusic.gui.render.GuiRender;
import com.codexceed.xmusic.gui.render.HoverTracker;
import com.codexceed.xmusic.gui.theme.GuiTheme;
import com.codexceed.xmusic.gui.util.AnimationHelper;
import com.codexceed.xmusic.service.ServiceManager;
import com.codexceed.xmusic.service.youtube.YouTubeToolManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Main music player screen.
 * <p>
 * Animation: The entire GUI scales from center as ONE unit.
 * Purely linear interpolation (no easing) for smooth, consistent motion.
 * Animation can be disabled or speed-adjusted via config.
 * <p>
 * Route memory: First open goes to Home. Subsequent opens restore the
 * last active route the user was viewing.
 */
public final class XMusicScreen extends Screen {
    private final TopBar topBar = new TopBar();
    private final SidebarNav sidebar = new SidebarNav();
    private final ContentHost content = new ContentHost();
    private final PlayerBar playerBar = new PlayerBar();

    // â”€â”€ Route persistence (static â€” survives screen close/reopen) â”€â”€â”€â”€â”€â”€â”€â”€
    private static GuiRoute lastRoute = null; // null = first launch
    private GuiRoute activeRoute;

    // â”€â”€ Animation state â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private long animStartMs = 0;
    private boolean closing = false;

    public XMusicScreen() {
        super(Component.literal(XMusic.MOD_NAME));

        // First launch â†’ Home; subsequent â†’ restore last route
        if (lastRoute == null) {
            activeRoute = GuiRoute.HOME;
        } else {
            activeRoute = lastRoute;
        }

        content.setRouteChanger(() -> activeRoute = GuiRoute.LIBRARY);
        animStartMs = System.currentTimeMillis();
    }

    // â”€â”€ Background â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // No dark overlay â€” game world stays fully visible
    }

    // â”€â”€ Animation helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Get effective intro duration in ms, respecting animation speed config. */
    private long getIntroDuration() {
        XMusicConfig cfg = ConfigManager.get();
        if (!cfg.animationsEnabled) return 0;
        float speed = Math.max(0.1f, cfg.animationSpeed);
        return (long) (GuiTheme.INTRO_DURATION_MS / speed);
    }

    /** Get effective outro duration in ms, respecting animation speed config. */
    private long getOutroDuration() {
        XMusicConfig cfg = ConfigManager.get();
        if (!cfg.animationsEnabled) return 0;
        float speed = Math.max(0.1f, cfg.animationSpeed);
        return (long) (GuiTheme.OUTRO_DURATION_MS / speed);
    }

    // â”€â”€ Main Render â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Update hover tracker delta
        HoverTracker.updateFrameDelta();

        // Save active route for next open
        lastRoute = activeRoute;

        // â”€â”€ 1. Calculate animation progress (purely linear) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        long elapsed = System.currentTimeMillis() - animStartMs;
        float progress;

        if (closing) {
            long duration = getOutroDuration();
            if (duration <= 0) {
                HoverTracker.reset();
                minecraft.setScreen(null);
                return;
            }
            progress = 1f - Math.min(1f, (float) elapsed / duration);
            if (elapsed >= duration) {
                HoverTracker.reset();
                minecraft.setScreen(null);
                return;
            }
        } else {
            long duration = getIntroDuration();
            if (duration <= 0) {
                progress = 1f; // instant
            } else {
                progress = Math.min(1f, (float) elapsed / duration);
            }
        }

        // â”€â”€ 2. No background overlay â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

        GuiFrame frame = GuiFrame.calculate(width, height);

        int fx = frame.x();
        int fy = frame.y();
        int fw = frame.width();
        int fh = frame.height();

        // â”€â”€ 3. Single unified transform: linear scale from center â”€â”€â”€â”€â”€â”€â”€â”€
        float scale = 0.92f + 0.08f * progress;   // 92% â†’ 100% (linear)
        float alpha = progress;                     // 0 â†’ 1 (linear)

        float centerX = fx + fw / 2f;
        float centerY = fy + fh / 2f;

        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-centerX, -centerY);

        // â”€â”€ 4. Frame background â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        int frameTopColor = AnimationHelper.withAlpha(GuiTheme.FRAME_TOP, alpha);
        int frameBotColor = AnimationHelper.withAlpha(GuiTheme.FRAME_BOTTOM, alpha);
        GuiRender.gradientV(graphics, fx, fy, fw, fh, frameTopColor, frameBotColor);
        if (progress > 0.3f) {
            GuiRender.mcFrameBorder(graphics, fx, fy, fw, fh);
        }

        // â”€â”€ 5. Render ALL children together â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        topBar.render(graphics, font, frame, mouseX, mouseY);
        sidebar.render(graphics, font, frame, activeRoute, mouseX, mouseY);
        content.render(graphics, font, frame, activeRoute, mouseX, mouseY);
        playerBar.render(graphics, font, frame, mouseX, mouseY);

        graphics.pose().popMatrix();

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    // â”€â”€ Input Events â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean someBool) {
        if (closing) return false;
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        GuiFrame frame = GuiFrame.calculate(width, height);

        if (topBar.closeClicked(frame, mouseX, mouseY)) {
            closeAnimated();
            return true;
        }

        GuiRoute clickedRoute = sidebar.clicked(frame, mouseX, mouseY);
        if (clickedRoute != null) {
            activeRoute = clickedRoute;
            lastRoute = activeRoute;
            return true;
        }

        if (playerBar.mouseClicked(frame, mouseX, mouseY)) {
            return true;
        }

        if (content.mouseClicked(frame, activeRoute, mouseX, mouseY, button)) {
            return true;
        }

        return super.mouseClicked(event, someBool);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        GuiFrame frame = GuiFrame.calculate(width, height);
        if (playerBar.mouseReleased(frame, mouseX, mouseY)) {
            return true;
        }
        if (content.mouseReleased(frame, activeRoute, mouseX, mouseY)) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        GuiFrame frame = GuiFrame.calculate(width, height);
        if (playerBar.mouseDragged(frame, mouseX, mouseY)) {
            return true;
        }
        if (content.mouseDragged(frame, activeRoute, mouseX, mouseY)) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
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
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.scancode();
        int modifiers = event.modifiers();
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
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char codePoint = (char) event.codepoint();
        if (playerBar.charTyped(codePoint, 0)) {
            return true;
        }
        if (content.charTyped(activeRoute, codePoint, 0)) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Trigger animated close instead of instant close. */
    private void closeAnimated() {
        if (!closing) {
            closing = true;
            lastRoute = activeRoute;
            animStartMs = System.currentTimeMillis();
        }
    }

    @Override
    public void onClose() {
        if (!closing) {
            closeAnimated();
        }
    }
}
