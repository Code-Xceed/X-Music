package com.codexceed.xmusic.gui.component;

import com.codexceed.xmusic.gui.GuiRoute;
import com.codexceed.xmusic.gui.layout.GuiFrame;
import com.codexceed.xmusic.gui.render.GuiRender;
import com.codexceed.xmusic.gui.theme.GuiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class ContentHost {
    private final HomeTab homeTab = new HomeTab();
    private final SearchTab searchTab = new SearchTab();
    private final LibraryTab libraryTab = new LibraryTab();
    private final DownloadsTab downloadsTab = new DownloadsTab();
    private final SettingsTab settingsTab = new SettingsTab();

    /** Callback to change the active route from a child tab. */
    private Runnable routeChanger = null;

    public void setRouteChanger(Runnable changer) {
        this.routeChanger = changer;
        homeTab.setNavigateToLibrary(() -> {
            libraryTab.openView("MOST_REPLAYED");
            if (changer != null) changer.run();
        });
    }

    public void render(GuiGraphics graphics, Font font, GuiFrame frame, GuiRoute route, int mouseX, int mouseY) {
        int x = frame.contentX();
        int y = frame.contentY();
        int w = frame.contentWidth();
        int h = frame.contentHeight();

        GuiRender.mcPanel(graphics, x, y, w, h);

        // Inner shadow: 1px darker line at top inside content area
        graphics.fill(x + 1, y + 1, x + w - 1, y + 2, GuiTheme.GLOW_DIM);

        if (route == GuiRoute.HOME) {
            homeTab.render(graphics, font, frame, mouseX, mouseY);
            return;
        }
        if (route == GuiRoute.SEARCH) {
            searchTab.render(graphics, font, frame, mouseX, mouseY);
            return;
        }
        if (route == GuiRoute.LIBRARY) {
            libraryTab.render(graphics, font, frame, mouseX, mouseY);
            return;
        }
        if (route == GuiRoute.DOWNLOADS) {
            downloadsTab.render(graphics, font, frame, mouseX, mouseY);
            return;
        }
        if (route == GuiRoute.SETTINGS) {
            settingsTab.render(graphics, font, frame, mouseX, mouseY);
            return;
        }
    }

    public boolean mouseClicked(GuiFrame frame, GuiRoute route, double mouseX, double mouseY, int button) {
        if (route == GuiRoute.HOME) {
            return homeTab.mouseClicked(frame, mouseX, mouseY, button);
        }
        if (route == GuiRoute.SEARCH) {
            return searchTab.mouseClicked(frame, mouseX, mouseY, button);
        }
        if (route == GuiRoute.LIBRARY) {
            return libraryTab.mouseClicked(frame, mouseX, mouseY, button);
        }
        if (route == GuiRoute.DOWNLOADS) {
            return downloadsTab.mouseClicked(frame, mouseX, mouseY, button);
        }
        if (route == GuiRoute.SETTINGS) {
            return settingsTab.mouseClicked(frame, mouseX, mouseY, button);
        }
        return false;
    }

    public boolean mouseScrolled(GuiFrame frame, GuiRoute route, double mouseX, double mouseY, double amount) {
        if (route == GuiRoute.HOME) {
            return homeTab.mouseScrolled(frame, mouseX, mouseY, amount);
        }
        if (route == GuiRoute.SEARCH) {
            return searchTab.mouseScrolled(frame, mouseX, mouseY, amount);
        }
        if (route == GuiRoute.LIBRARY) {
            return libraryTab.mouseScrolled(frame, mouseX, mouseY, amount);
        }
        if (route == GuiRoute.DOWNLOADS) {
            return downloadsTab.mouseScrolled(frame, mouseX, mouseY, amount);
        }
        if (route == GuiRoute.SETTINGS) {
            return settingsTab.mouseScrolled(frame, mouseX, mouseY, amount);
        }
        return false;
    }

    public boolean keyPressed(GuiRoute route, int keyCode, int scanCode, int modifiers) {
        // Home tab has no key input
        if (route == GuiRoute.SEARCH) {
            return searchTab.keyPressed(keyCode, scanCode, modifiers);
        }
        if (route == GuiRoute.LIBRARY) {
            return libraryTab.keyPressed(keyCode, scanCode, modifiers);
        }
        if (route == GuiRoute.DOWNLOADS) {
            return downloadsTab.keyPressed(keyCode, scanCode, modifiers);
        }
        if (route == GuiRoute.SETTINGS) {
            return settingsTab.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    public boolean charTyped(GuiRoute route, char codePoint, int modifiers) {
        // Home tab has no text input
        if (route == GuiRoute.SEARCH) {
            return searchTab.charTyped(codePoint, modifiers);
        }
        if (route == GuiRoute.LIBRARY) {
            return libraryTab.charTyped(codePoint, modifiers);
        }
        if (route == GuiRoute.DOWNLOADS) {
            return downloadsTab.charTyped(codePoint, modifiers);
        }
        if (route == GuiRoute.SETTINGS) {
            return settingsTab.charTyped(codePoint, modifiers);
        }
        return false;
    }

    public boolean mouseReleased(GuiFrame frame, GuiRoute route, double mouseX, double mouseY) {
        if (route == GuiRoute.SETTINGS) {
            return settingsTab.mouseReleased(frame, mouseX, mouseY);
        }
        return false;
    }

    public boolean mouseDragged(GuiFrame frame, GuiRoute route, double mouseX, double mouseY) {
        if (route == GuiRoute.SETTINGS) {
            return settingsTab.mouseDragged(frame, mouseX, mouseY);
        }
        return false;
    }

}
