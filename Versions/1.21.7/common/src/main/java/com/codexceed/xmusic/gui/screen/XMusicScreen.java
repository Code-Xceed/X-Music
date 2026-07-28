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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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

    // ── Route persistence (static — survives screen close/reopen) ────────
    private static GuiRoute lastRoute = null; // null = first launch
    private GuiRoute activeRoute;

    public GuiRoute getActiveRoute() {
        return activeRoute;
    }

    // ── Animation state ──────────────────────────────────────────────────
    private long animStartMs = 0;
    private boolean closing = false;
    private final Screen parentScreen;

    public XMusicScreen(Screen parentScreen) {
        super(Component.literal(XMusic.MOD_NAME));
        this.parentScreen = parentScreen;

        // First launch → Home; subsequent → restore last route
        if (lastRoute == null) {
            activeRoute = GuiRoute.HOME;
        } else {
            activeRoute = lastRoute;
        }

        content.setRouteChanger(() -> activeRoute = GuiRoute.LIBRARY);
        animStartMs = System.currentTimeMillis();
    }

    public XMusicScreen() {
        this(null);
    }

    // ── Background ───────────────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No dark overlay — game world stays fully visible
    }

        protected void renderBlurredBackground() {
        // Intentionally empty
    }

    // ── Animation helpers ────────────────────────────────────────────────

    /** Get effective intro duration in ms, respecting animation speed config. */
    private long getIntroDuration() {
        XMusicConfig cfg = ConfigManager.get();
        if (!cfg.animationsEnabled) return 0;
        float speed = Math.max(0.1f, cfg.animationSpeed * 3.0f);
        return (long) (GuiTheme.INTRO_DURATION_MS / speed);
    }

    /** Get effective outro duration in ms, respecting animation speed config. */
    private long getOutroDuration() {
        XMusicConfig cfg = ConfigManager.get();
        if (!cfg.animationsEnabled) return 0;
        float speed = Math.max(0.1f, cfg.animationSpeed * 3.0f);
        return (long) (GuiTheme.OUTRO_DURATION_MS / speed);
    }

    // ── Main Render ──────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render the parent screen (e.g. main menu, options) behind us so the background remains visible
        if (parentScreen != null) {
            try {
                parentScreen.render(graphics, -999, -999, partialTick);
            } catch (Throwable t) {
                XMusic.LOGGER.error("Failed to render parent screen", t);
            }
        }

        // Update hover tracker delta
        HoverTracker.updateFrameDelta();

        // Save active route for next open
        lastRoute = activeRoute;

        // ── 1. Calculate animation progress (purely linear) ──────────────
        long elapsed = System.currentTimeMillis() - animStartMs;
        float progress;

        if (closing) {
            long duration = getOutroDuration();
            if (duration <= 0) {
                HoverTracker.reset();
                minecraft.setScreen(parentScreen);
                return;
            }
            progress = 1f - Math.min(1f, (float) elapsed / duration);
            if (elapsed >= duration) {
                HoverTracker.reset();
                minecraft.setScreen(parentScreen);
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

        // ── 2. No background overlay ─────────────────────────────────────

        boolean scaleActive = isScaleActive();
        GuiFrame frame = getEffectiveFrame();

        int fx = frame.x();
        int fy = frame.y();
        int fw = frame.width();
        int fh = frame.height();

        // ── 3. Single unified transform: premium spring/eased scale from center ────────
        float easedProgress = closing ? AnimationHelper.easeIn(progress) : AnimationHelper.easeOutBack(progress);
        float scale = 0.92f + 0.08f * easedProgress;
        float alpha = AnimationHelper.clamp01(progress);

        float centerX = fx + fw / 2f;
        float centerY = fy + fh / 2f;

        graphics.pose().pushMatrix();
        if (scaleActive) {
            graphics.pose().scale(2.0f, 2.0f);
        }
        graphics.pose().translate(0, 0);
        graphics.pose().translate(centerX, centerY);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-centerX, -centerY);

        // ── 4. Frame background & Border ─────────────────────────────────
        int frameTopColor = AnimationHelper.withAlpha(GuiTheme.FRAME_TOP, alpha);
        int frameBotColor = AnimationHelper.withAlpha(GuiTheme.FRAME_BOTTOM, alpha);
        GuiRender.gradientV(graphics, fx, fy, fw, fh, frameTopColor, frameBotColor);
        GuiRender.mcFrameBorder(graphics, fx, fy, fw, fh, alpha);

        // ── 5. Render ALL children together ──────────────────────────────
        int drawMouseX = mouseX;
        int drawMouseY = mouseY;
        if (scaleActive) {
            drawMouseX /= 2;
            drawMouseY /= 2;
        }

        topBar.render(graphics, font, frame, drawMouseX, drawMouseY);
        sidebar.render(graphics, font, frame, activeRoute, drawMouseX, drawMouseY);
        content.render(graphics, font, frame, activeRoute, drawMouseX, drawMouseY);
        playerBar.render(graphics, font, frame, drawMouseX, drawMouseY);

        graphics.pose().popMatrix();

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private boolean isScaleActive() {
        return minecraft != null && ((int) minecraft.getWindow().getGuiScale()) == 1;
    }

    private GuiFrame getEffectiveFrame() {
        if (isScaleActive()) {
            return GuiFrame.calculate(width / 2, height / 2);
        }
        return GuiFrame.calculate(width, height);
    }

    // ── Input Events ─────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) return false;
        GuiRender.soundPlayedThisFrame = false;
        if (isScaleActive()) {
            mouseX /= 2.0;
            mouseY /= 2.0;
        }
        GuiFrame frame = getEffectiveFrame();

        if (topBar.closeClicked(frame, mouseX, mouseY)) {
            GuiRender.playClickSound(0.85f);
            closeAnimated();
            return true;
        }
 
        GuiRoute clickedRoute = sidebar.clicked(frame, mouseX, mouseY);
        if (clickedRoute != null) {
            GuiRender.playTabSound();
            activeRoute = clickedRoute;
            lastRoute = activeRoute;
            return true;
        }

        if (playerBar.mouseClicked(frame, mouseX, mouseY)) {
            if (!GuiRender.soundPlayedThisFrame) {
                GuiRender.playClickSound(1.0f);
            }
            return true;
        }

        if (content.mouseClicked(frame, activeRoute, mouseX, mouseY, button)) {
            if (!GuiRender.soundPlayedThisFrame) {
                GuiRender.playClickSound(1.0f);
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isScaleActive()) {
            mouseX /= 2.0;
            mouseY /= 2.0;
        }
        GuiFrame frame = getEffectiveFrame();
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
        if (isScaleActive()) {
            mouseX /= 2.0;
            mouseY /= 2.0;
        }
        GuiFrame frame = getEffectiveFrame();
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
        if (isScaleActive()) {
            mouseX /= 2.0;
            mouseY /= 2.0;
        }
        GuiFrame frame = getEffectiveFrame();
        if (sidebar.mouseScrolled(frame, mouseX, mouseY, amountY)) {
            return true;
        }
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
    public void closeAnimated() {
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

