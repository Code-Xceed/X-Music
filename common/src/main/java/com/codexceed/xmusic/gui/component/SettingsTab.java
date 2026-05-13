package com.codexceed.xmusic.gui.component;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.audio.PlaybackMode;
import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.config.XMusicConfig;
import com.codexceed.xmusic.download.DownloadManager;
import com.codexceed.xmusic.gui.layout.GuiFrame;
import com.codexceed.xmusic.gui.render.GuiRender;
import com.codexceed.xmusic.gui.render.HoverTracker;
import com.codexceed.xmusic.gui.render.IconRenderer;
import com.codexceed.xmusic.gui.theme.GuiTheme;
import com.codexceed.xmusic.player.PlayerFacade;
import com.codexceed.xmusic.service.ServiceManager;
import com.codexceed.xmusic.service.local.LocalMusicService;
import com.codexceed.xmusic.service.youtube.YouTubeToolManager;
import com.codexceed.xmusic.source.TrackRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings tab — hybrid layout with collapsible category sections.
 * Compact toggle/cycle/slider rows for simple settings, expandable panels
 * for categories needing more control.
 */
public final class SettingsTab {

    // ── Layout ─────────────────────────────────────────────────────────────
    private static final int PAD = 8;
    private static final int ROW_H = 22;
    private static final int SECTION_HEADER_H = 24;
    private static final int SECTION_GAP = 4;
    private static final int TOGGLE_W = 40;
    private static final int TOGGLE_H = 14;
    private static final int SLIDER_W = 100;
    private static final int SLIDER_H = 14;
    private static final int BTN_H = 18;
    private static final int PATH_DISPLAY_W = 120;

    // ── State ──────────────────────────────────────────────────────────────
    private boolean playbackExpanded = true;
    private boolean hudExpanded = false;
    private boolean youtubeExpanded = false;
    private boolean storageExpanded = false;
    private boolean aboutExpanded = false;

    private double scrollOffset = 0;
    private double targetScroll = 0;
    private static final float SCROLL_LERP = 0.18f;

    // Slider drag state
    private String draggingSlider = null; // field name being dragged
    private boolean confirmReset = false;

    // Text input state for path fields
    private String editingField = null; // field name being edited
    private String editText = "";

    // ── Render ─────────────────────────────────────────────────────────────

    public void render(GuiGraphics graphics, Font font, GuiFrame frame, int mouseX, int mouseY) {
        int x = frame.contentX();
        int y = frame.contentY();
        int w = frame.contentWidth();
        int h = frame.contentHeight();

        GuiRender.mcPanel(graphics, x, y, w, h);

        // Smooth scroll
        smoothScroll();

        int contentX = x + PAD;
        int contentW = w - PAD * 2;
        int drawY = y + PAD - (int) scrollOffset;

        // Enable scissor for content area
        graphics.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);

        // Title
        GuiRender.shadowText(graphics, font, "Settings", contentX, drawY, GuiTheme.ACCENT);
        drawY += 16;

        // Sections
        drawY = renderSection(graphics, font, contentX, drawY, contentW, mouseX, mouseY,
                "Playback", "playback", playbackExpanded, this::renderPlaybackRows);
        drawY = renderSection(graphics, font, contentX, drawY, contentW, mouseX, mouseY,
                "HUD Overlay", "hud", hudExpanded, this::renderHudRows);
        drawY = renderSection(graphics, font, contentX, drawY, contentW, mouseX, mouseY,
                "YouTube & Downloads", "youtube", youtubeExpanded, this::renderYouTubeRows);
        drawY = renderSection(graphics, font, contentX, drawY, contentW, mouseX, mouseY,
                "Storage", "storage", storageExpanded, this::renderStorageRows);
        drawY = renderSection(graphics, font, contentX, drawY, contentW, mouseX, mouseY,
                "About", "about", aboutExpanded, this::renderAboutRows);

        // Store max scroll for clamping
        int contentHeight = drawY + (int) scrollOffset - y - h + PAD;
        if (contentHeight < 0) contentHeight = 0;
        maxScroll = contentHeight;

        graphics.disableScissor();
    }

    private double maxScroll = 0;

    private void smoothScroll() {
        scrollOffset += (targetScroll - scrollOffset) * SCROLL_LERP;
        if (Math.abs(scrollOffset - targetScroll) < 0.5) scrollOffset = targetScroll;
        // Clamp
        if (scrollOffset < 0) { scrollOffset = 0; targetScroll = 0; }
        if (scrollOffset > maxScroll) { scrollOffset = maxScroll; targetScroll = maxScroll; }
    }

    // ── Section rendering ──────────────────────────────────────────────────

    @FunctionalInterface
    private interface SectionRenderer {
        int render(GuiGraphics g, Font f, int x, int y, int w, int mx, int my);
    }

    private int renderSection(GuiGraphics g, Font f, int x, int y, int w,
                              int mx, int my, String title, String id,
                              boolean expanded, SectionRenderer renderer) {
        // Section header
        boolean headerHover = GuiRender.inside(mx, my, x, y, w, SECTION_HEADER_H);
        float lerp = HoverTracker.tick("settings_sec_" + id, headerHover);

        // Header background
        int headerBg = headerHover ? GuiTheme.PANEL_HOVER : GuiTheme.PANEL_DARK;
        g.fill(x, y, x + w, y + SECTION_HEADER_H, headerBg);
        GuiRender.bevel(g, x, y, w, SECTION_HEADER_H, true);

        // Expand/collapse indicator
        String arrow = expanded ? "▼" : "▶";
        int arrowColor = expanded ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED;
        GuiRender.shadowText(g, f, arrow, x + 4, y + (SECTION_HEADER_H - 8) / 2, arrowColor);

        // Section title
        GuiRender.shadowText(g, f, title, x + 16, y + (SECTION_HEADER_H - 8) / 2,
                expanded ? GuiTheme.TEXT : GuiTheme.TEXT_SOFT);

        y += SECTION_HEADER_H + 2;

        // Expanded content
        if (expanded) {
            y = renderer.render(g, f, x, y, w, mx, my);
        }

        y += SECTION_GAP;
        return y;
    }

    // ── Playback rows ──────────────────────────────────────────────────────

    private int renderPlaybackRows(GuiGraphics g, Font f, int x, int y, int w, int mx, int my) {
        XMusicConfig cfg = ConfigManager.get();
        PlayerFacade player = PlayerFacade.getInstance();

        // Autoplay toggle
        y = renderToggleRow(g, f, x, y, w, mx, my, "Autoplay",
                "autoplay", player.isAutoplay());

        // Playback Mode cycle
        PlaybackMode mode = player.getPlaybackMode();
        String modeLabel = mode.getDisplayName();
        y = renderCycleRow(g, f, x, y, w, mx, my, "Playback Mode",
                "playbackMode", modeLabel);

        // Volume Step
        y = renderSliderRow(g, f, x, y, w, mx, my, "Volume Step",
                "volumeStep", cfg.volumeStep, 0.05f, 0.25f, 0.01f,
                String.format("%.0f%%", cfg.volumeStep * 100));

        return y;
    }

    // ── HUD rows ────────────────────────────────────────────────────────────

    private int renderHudRows(GuiGraphics g, Font f, int x, int y, int w, int mx, int my) {
        XMusicConfig cfg = ConfigManager.get();

        // HUD Enabled toggle
        y = renderToggleRow(g, f, x, y, w, mx, my, "HUD Enabled",
                "hudEnabled", cfg.hudEnabled);

        // HUD Position cycle
        String posLabel = cfg.hudPosition.replace("_", " ");
        y = renderCycleRow(g, f, x, y, w, mx, my, "HUD Position",
                "hudPosition", posLabel);

        // Auto-Hide slider
        y = renderSliderRow(g, f, x, y, w, mx, my, "Auto-Hide",
                "hudAutoHideSeconds", cfg.hudAutoHideSeconds, 0, 30, 1,
                cfg.hudAutoHideSeconds == 0 ? "Always" : cfg.hudAutoHideSeconds + "s");

        // Now Playing Toast toggle
        y = renderToggleRow(g, f, x, y, w, mx, my, "Now Playing Toast",
                "showNowPlayingToast", cfg.showNowPlayingToast);

        // Edit HUD Position button
        y = renderActionRow(g, f, x, y, w, mx, my, "", "editHudPosition", "Edit HUD Position");

        return y;
    }

    // ── YouTube rows ───────────────────────────────────────────────────────

    private int renderYouTubeRows(GuiGraphics g, Font f, int x, int y, int w, int mx, int my) {
        XMusicConfig cfg = ConfigManager.get();
        YouTubeToolManager tools = ServiceManager.getYouTubeToolManager();

        // Tool status
        String statusText;
        int statusColor;
        if (tools == null) {
            statusText = "Unavailable";
            statusColor = GuiTheme.TEXT_MUTED;
        } else {
            YouTubeToolManager.SetupState st = tools.getState();
            switch (st) {
                case READY:
                    statusText = "✓ Ready";
                    statusColor = GuiTheme.ACCENT;
                    break;
                case INSTALLING:
                    statusText = tools.getInstallStep().label;
                    statusColor = GuiTheme.ACCENT_DARK;
                    break;
                case CHECKING:
                    statusText = "Checking...";
                    statusColor = GuiTheme.TEXT_SOFT;
                    break;
                case MISSING:
                    statusText = "Missing";
                    statusColor = GuiTheme.DANGER;
                    break;
                case ERROR:
                    statusText = "Error";
                    statusColor = GuiTheme.DANGER;
                    break;
                default:
                    statusText = "Unknown";
                    statusColor = GuiTheme.TEXT_MUTED;
            }
        }

        y = renderStatusRow(g, f, x, y, w, mx, my, "Tool Status", statusText, statusColor);

        // Install/Reinstall button (if tools missing or error)
        if (tools != null && (tools.getState() == YouTubeToolManager.SetupState.MISSING
                || tools.getState() == YouTubeToolManager.SetupState.ERROR)) {
            y = renderActionRow(g, f, x, y, w, mx, my, "", "installTools", "Install Tools");
        } else if (tools != null && tools.getState() == YouTubeToolManager.SetupState.READY) {
            y = renderActionRow(g, f, x, y, w, mx, my, "", "reinstallTools", "Reinstall");
        }

        // yt-dlp Path
        y = renderPathRow(g, f, x, y, w, mx, my, "yt-dlp Path",
                "youtubeYtDlpPath", cfg.youtubeYtDlpPath.isEmpty() ? "auto" : cfg.youtubeYtDlpPath);

        // ffmpeg Path
        y = renderPathRow(g, f, x, y, w, mx, my, "ffmpeg Path",
                "youtubeFfmpegPath", cfg.youtubeFfmpegPath.isEmpty() ? "auto" : cfg.youtubeFfmpegPath);

        // Download Timeout
        y = renderSliderRow(g, f, x, y, w, mx, my, "Download Timeout",
                "youtubeDownloadTimeoutSeconds", cfg.youtubeDownloadTimeoutSeconds, 60, 600, 30,
                cfg.youtubeDownloadTimeoutSeconds + "s");

        // Concurrent Fragments
        y = renderSliderRow(g, f, x, y, w, mx, my, "Concurrent Fragments",
                "youtubeDownloadConcurrentFragments", cfg.youtubeDownloadConcurrentFragments, 1, 8, 1,
                String.valueOf(cfg.youtubeDownloadConcurrentFragments));

        // Cookies File
        y = renderPathRow(g, f, x, y, w, mx, my, "Cookies File",
                "youtubeCookiesFile", cfg.youtubeCookiesFile.isEmpty() ? "—" : cfg.youtubeCookiesFile);

        return y;
    }

    // ── Storage rows ───────────────────────────────────────────────────────

    private int renderStorageRows(GuiGraphics g, Font f, int x, int y, int w, int mx, int my) {
        XMusicConfig cfg = ConfigManager.get();

        // Local Music Folder
        LocalMusicService localService = ServiceManager.getLocalMusic();
        String localPath = localService != null
                ? localService.getMusicDirectory().toString()
                : (cfg.localMusicDirectory.isEmpty() ? "default" : cfg.localMusicDirectory);
        y = renderFolderRow(g, f, x, y, w, mx, my, "Local Music Folder",
                "openLocalFolder", localPath);

        // Downloads Folder
        Path dlDir = DownloadManager.getInstance().getDownloadsDir();
        String dlPath = dlDir != null ? dlDir.toString() : "default";
        y = renderFolderRow(g, f, x, y, w, mx, my, "Downloads Folder",
                "openDownloadsFolder", dlPath);

        // Cache Limit
        y = renderSliderRow(g, f, x, y, w, mx, my, "Cache Limit",
                "youtubeCacheMaxSizeMb", cfg.youtubeCacheMaxSizeMb, 128, 2048, 64,
                cfg.youtubeCacheMaxSizeMb + " MB");

        // Max Cached Tracks
        y = renderSliderRow(g, f, x, y, w, mx, my, "Max Cached Tracks",
                "youtubeCacheMaxTracks", cfg.youtubeCacheMaxTracks, 8, 64, 4,
                String.valueOf(cfg.youtubeCacheMaxTracks));

        // Clear Cache
        y = renderActionRow(g, f, x, y, w, mx, my, "", "clearCache", "Clear Cache");

        return y;
    }

    // ── About rows ─────────────────────────────────────────────────────────

    private int renderAboutRows(GuiGraphics g, Font f, int x, int y, int w, int mx, int my) {
        // Version
        y = renderLabelRow(g, f, x, y, w, "Version", "CodeX Music Player v" + XMusic.getVersion());

        // Reset All Settings
        if (confirmReset) {
            y = renderActionRow(g, f, x, y, w, mx, my, "Are you sure?", "confirmReset", "Yes, Reset");
            y = renderActionRow(g, f, x, y, w, mx, my, "", "cancelReset", "Cancel");
        } else {
            y = renderActionRow(g, f, x, y, w, mx, my, "", "resetAll", "Reset All Settings");
        }

        return y;
    }

    // ── Row type renderers ─────────────────────────────────────────────────

    private int renderToggleRow(GuiGraphics g, Font f, int x, int y, int w,
                                int mx, int my, String label, String id, boolean value) {
        // Row background on hover
        boolean rowHover = GuiRender.inside(mx, my, x, y, w, ROW_H);
        if (rowHover) g.fill(x, y, x + w, y + ROW_H, GuiTheme.PANEL_HOVER);

        // Label
        GuiRender.shadowText(g, f, label, x + 4, y + (ROW_H - 8) / 2, GuiTheme.TEXT);

        // Toggle switch
        int toggleX = x + w - TOGGLE_W - 4;
        int toggleY = y + (ROW_H - TOGGLE_H) / 2;
        boolean toggleHover = GuiRender.inside(mx, my, toggleX, toggleY, TOGGLE_W, TOGGLE_H);

        // Toggle track
        int trackBg = value ? GuiTheme.ACCENT_DARK : GuiTheme.PANEL_DARK;
        g.fill(toggleX, toggleY, toggleX + TOGGLE_W, toggleY + TOGGLE_H, trackBg);
        GuiRender.bevel(g, toggleX, toggleY, TOGGLE_W, TOGGLE_H, true);

        // Toggle knob
        int knobSize = TOGGLE_H - 4;
        int knobX = value ? toggleX + TOGGLE_W - knobSize - 2 : toggleX + 2;
        int knobColor = value ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED;
        g.fill(knobX, toggleY + 2, knobX + knobSize, toggleY + 2 + knobSize, knobColor);

        return y + ROW_H;
    }

    private int renderCycleRow(GuiGraphics g, Font f, int x, int y, int w,
                               int mx, int my, String label, String id, String value) {
        boolean rowHover = GuiRender.inside(mx, my, x, y, w, ROW_H);
        if (rowHover) g.fill(x, y, x + w, y + ROW_H, GuiTheme.PANEL_HOVER);

        // Label
        GuiRender.shadowText(g, f, label, x + 4, y + (ROW_H - 8) / 2, GuiTheme.TEXT);

        // Cycle button showing current value
        int btnW = f.width(value) + 16;
        int btnX = x + w - btnW - 4;
        int btnY = y + (ROW_H - BTN_H) / 2;
        boolean btnHover = GuiRender.inside(mx, my, btnX, btnY, btnW, BTN_H);
        GuiRender.mcButton(g, btnX, btnY, btnW, BTN_H, btnHover, false);
        int textColor = btnHover ? GuiTheme.ACCENT : GuiTheme.TEXT_SOFT;
        GuiRender.text(g, f, value, btnX + 8, btnY + (BTN_H - 8) / 2, textColor);

        return y + ROW_H;
    }

    private int renderSliderRow(GuiGraphics g, Font f, int x, int y, int w, int mx, int my,
                                String label, String field, double value, double min, double max,
                                double step, String displayValue) {
        boolean rowHover = GuiRender.inside(mx, my, x, y, w, ROW_H);
        if (rowHover) g.fill(x, y, x + w, y + ROW_H, GuiTheme.PANEL_HOVER);

        // Label
        GuiRender.shadowText(g, f, label, x + 4, y + (ROW_H - 8) / 2, GuiTheme.TEXT);

        // Value display
        int valW = f.width(displayValue) + 4;
        int valX = x + w - valW - SLIDER_W - 12;
        GuiRender.text(g, f, displayValue, valX, y + (ROW_H - 8) / 2, GuiTheme.TEXT_MUTED);

        // Slider
        int sliderX = x + w - SLIDER_W - 4;
        int sliderY = y + (ROW_H - SLIDER_H) / 2;
        boolean sliderHover = GuiRender.inside(mx, my, sliderX, sliderY, SLIDER_W, SLIDER_H)
                || field.equals(draggingSlider);

        // Slider track (MC well)
        GuiRender.mcWell(g, sliderX, sliderY, SLIDER_W, SLIDER_H);

        // Slider fill
        float pct = (float) ((value - min) / (max - min));
        if (pct < 0) pct = 0;
        if (pct > 1) pct = 1;
        int fillW = (int) (SLIDER_W * pct);
        if (fillW > 0) {
            g.fill(sliderX + 1, sliderY + 1, sliderX + fillW, sliderY + SLIDER_H - 1, GuiTheme.ACCENT_DARK);
        }

        // Slider knob
        int knobX = sliderX + fillW;
        if (sliderHover || field.equals(draggingSlider)) {
            GuiRender.mcButton(g, knobX - 3, sliderY - 1, 6, SLIDER_H + 2, true, false);
        } else if (fillW > 0) {
            g.fill(knobX - 2, sliderY, knobX + 2, sliderY + SLIDER_H, GuiTheme.TEXT_SOFT);
        }

        return y + ROW_H;
    }

    private int renderStatusRow(GuiGraphics g, Font f, int x, int y, int w, int mx, int my,
                                String label, String status, int statusColor) {
        GuiRender.shadowText(g, f, label, x + 4, y + (ROW_H - 8) / 2, GuiTheme.TEXT);

        int statusW = f.width(status) + 4;
        int statusX = x + w - statusW - 4;
        GuiRender.shadowText(g, f, status, statusX, y + (ROW_H - 8) / 2, statusColor);

        return y + ROW_H;
    }

    private int renderPathRow(GuiGraphics g, Font f, int x, int y, int w, int mx, int my,
                              String label, String field, String value) {
        boolean rowHover = GuiRender.inside(mx, my, x, y, w, ROW_H);
        if (rowHover) g.fill(x, y, x + w, y + ROW_H, GuiTheme.PANEL_HOVER);

        // Label
        GuiRender.shadowText(g, f, label, x + 4, y + (ROW_H - 8) / 2, GuiTheme.TEXT);

        // Path display (truncated)
        boolean isEditing = field.equals(editingField);
        int pathX = x + w - PATH_DISPLAY_W - 4;
        int pathY = y + (ROW_H - 14) / 2;

        if (isEditing) {
            // Inline text input
            GuiRender.mcWell(g, pathX, pathY, PATH_DISPLAY_W, 14);
            String display = editText;
            if (display.isEmpty()) display = " ";
            // Truncate from left
            String fits = f.plainSubstrByWidth(display, PATH_DISPLAY_W - 6);
            if (!fits.equals(display) && display.length() > fits.length()) {
                fits = "…" + display.substring(display.length() - fits.length());
            }
            GuiRender.text(g, f, fits, pathX + 3, pathY + 3, GuiTheme.TEXT);
            // Cursor blink
            if (System.currentTimeMillis() % 1000 < 500) {
                int cursorX = pathX + 3 + f.width(fits);
                g.fill(cursorX, pathY + 2, cursorX + 1, pathY + 12, GuiTheme.ACCENT);
            }
        } else {
            // Read-only display
            int textColor = value.equals("auto") || value.equals("—")
                    ? GuiTheme.TEXT_MUTED : GuiTheme.TEXT_SOFT;
            GuiRender.truncated(g, f, value, pathX + 2, pathY + 3, PATH_DISPLAY_W - 4, textColor);
        }

        return y + ROW_H;
    }

    private int renderFolderRow(GuiGraphics g, Font f, int x, int y, int w, int mx, int my,
                                String label, String actionId, String path) {
        boolean rowHover = GuiRender.inside(mx, my, x, y, w, ROW_H);
        if (rowHover) g.fill(x, y, x + w, y + ROW_H, GuiTheme.PANEL_HOVER);

        // Label
        GuiRender.shadowText(g, f, label, x + 4, y + (ROW_H - 8) / 2, GuiTheme.TEXT);

        // Path display (truncated)
        int pathX = x + 4 + f.width(label) + 8;
        int pathW = w - f.width(label) - 8 - 78 - 8;
        if (pathW > 40) {
            GuiRender.truncated(g, f, path, pathX, y + (ROW_H - 8) / 2, pathW, GuiTheme.TEXT_MUTED);
        }

        // Open button
        String btnLabel = "Open Folder";
        int btnW = f.width(btnLabel) + 12;
        int btnX = x + w - btnW - 4;
        int btnY = y + (ROW_H - BTN_H) / 2;
        boolean btnHover = GuiRender.inside(mx, my, btnX, btnY, btnW, BTN_H);
        GuiRender.mcButton(g, btnX, btnY, btnW, BTN_H, btnHover, false);
        int textColor = btnHover ? GuiTheme.ACCENT : GuiTheme.TEXT_SOFT;
        GuiRender.text(g, f, btnLabel, btnX + 6, btnY + (BTN_H - 8) / 2, textColor);

        return y + ROW_H;
    }

    private int renderActionRow(GuiGraphics g, Font f, int x, int y, int w, int mx, int my,
                                String label, String actionId, String btnLabel) {
        if (!label.isEmpty()) {
            GuiRender.shadowText(g, f, label, x + 4, y + (ROW_H - 8) / 2, GuiTheme.TEXT_SOFT);
        }

        int btnW = f.width(btnLabel) + 16;
        int btnX = label.isEmpty() ? x + w - btnW - 4 : x + w - btnW - 4;
        int btnY = y + (ROW_H - BTN_H) / 2;
        boolean btnHover = GuiRender.inside(mx, my, btnX, btnY, btnW, BTN_H);
        int btnBg = actionId.equals("confirmReset") ? GuiTheme.PANEL_ACTIVE : 0;
        if (btnBg != 0) {
            g.fill(btnX, btnY, btnX + btnW, btnY + BTN_H, btnBg);
        }
        GuiRender.mcButton(g, btnX, btnY, btnW, BTN_H, btnHover, false);
        int textColor = actionId.equals("confirmReset") ? GuiTheme.DANGER
                : (btnHover ? GuiTheme.ACCENT : GuiTheme.TEXT_SOFT);
        GuiRender.text(g, f, btnLabel, btnX + 8, btnY + (BTN_H - 8) / 2, textColor);

        return y + ROW_H;
    }

    private int renderLabelRow(GuiGraphics g, Font f, int x, int y, int w,
                               String label, String value) {
        GuiRender.shadowText(g, f, label, x + 4, y + (ROW_H - 8) / 2, GuiTheme.TEXT);
        int valW = f.width(value);
        int valX = x + w - valW - 4;
        GuiRender.text(g, f, value, valX, y + (ROW_H - 8) / 2, GuiTheme.TEXT_MUTED);
        return y + ROW_H;
    }

    // ── Mouse Click ────────────────────────────────────────────────────────

    public boolean mouseClicked(GuiFrame frame, double mouseX, double mouseY, int button) {
        int x = frame.contentX();
        int y = frame.contentY();
        int w = frame.contentWidth();
        int h = frame.contentHeight();

        int contentX = x + PAD;
        int contentW = w - PAD * 2;
        int drawY = y + PAD - (int) scrollOffset;

        // Title
        drawY += 16;

        // Check section headers
        drawY = checkSectionClick(contentX, drawY, contentW, mouseX, mouseY,
                "playback", playbackExpanded);
        if (playbackExpanded) {
            drawY = checkPlaybackClicks(contentX, drawY, contentW, mouseX, mouseY);
        }
        drawY = checkSectionClick(contentX, drawY, contentW, mouseX, mouseY,
                "hud", hudExpanded);
        if (hudExpanded) {
            drawY = checkHudClicks(contentX, drawY, contentW, mouseX, mouseY);
        }
        drawY = checkSectionClick(contentX, drawY, contentW, mouseX, mouseY,
                "youtube", youtubeExpanded);
        if (youtubeExpanded) {
            drawY = checkYouTubeClicks(contentX, drawY, contentW, mouseX, mouseY);
        }
        drawY = checkSectionClick(contentX, drawY, contentW, mouseX, mouseY,
                "storage", storageExpanded);
        if (storageExpanded) {
            drawY = checkStorageClicks(contentX, drawY, contentW, mouseX, mouseY);
        }
        drawY = checkSectionClick(contentX, drawY, contentW, mouseX, mouseY,
                "about", aboutExpanded);
        if (aboutExpanded) {
            drawY = checkAboutClicks(contentX, drawY, contentW, mouseX, mouseY);
        }

        return false;
    }

    private int checkSectionClick(int x, int y, int w, double mx, double my,
                                  String id, boolean expanded) {
        if (GuiRender.inside(mx, my, x, y, w, SECTION_HEADER_H)) {
            toggleSection(id);
            return y + SECTION_HEADER_H + 2 + (expanded ? 0 : SECTION_GAP);
        }
        y += SECTION_HEADER_H + 2;
        // Skip rows if expanded (we don't know exact height here, but click handling
        // will check each row individually)
        return y;
    }

    private void toggleSection(String id) {
        switch (id) {
            case "playback": playbackExpanded = !playbackExpanded; break;
            case "hud": hudExpanded = !hudExpanded; break;
            case "youtube": youtubeExpanded = !youtubeExpanded; break;
            case "storage": storageExpanded = !storageExpanded; break;
            case "about": aboutExpanded = !aboutExpanded; break;
        }
    }

    // ── Playback click handling ────────────────────────────────────────────

    private int checkPlaybackClicks(int x, int y, int w, double mx, double my) {
        PlayerFacade player = PlayerFacade.getInstance();

        // Autoplay toggle
        if (GuiRender.inside(mx, my, x, y, w, ROW_H)) {
            int toggleX = x + w - TOGGLE_W - 4;
            int toggleY = y + (ROW_H - TOGGLE_H) / 2;
            if (GuiRender.inside(mx, my, toggleX, toggleY, TOGGLE_W, TOGGLE_H)) {
                player.toggleAutoplay();
                return y + ROW_H;
            }
        }
        y += ROW_H;

        // Playback Mode cycle
        if (GuiRender.inside(mx, my, x, y, w, ROW_H)) {
            player.cyclePlaybackMode();
            return y + ROW_H;
        }
        y += ROW_H;

        // Volume Step slider
        y = checkSliderClick(x, y, w, mx, my, "volumeStep");
        y += ROW_H;

        return y;
    }

    // ── HUD click handling ──────────────────────────────────────────────────

    private int checkHudClicks(int x, int y, int w, double mx, double my) {
        XMusicConfig cfg = ConfigManager.get();

        // HUD Enabled toggle
        if (GuiRender.inside(mx, my, x, y, w, ROW_H)) {
            int toggleX = x + w - TOGGLE_W - 4;
            int toggleY = y + (ROW_H - TOGGLE_H) / 2;
            if (GuiRender.inside(mx, my, toggleX, toggleY, TOGGLE_W, TOGGLE_H)) {
                cfg.hudEnabled = !cfg.hudEnabled;
                ConfigManager.save();
                return y + ROW_H;
            }
        }
        y += ROW_H;

        // HUD Position cycle
        if (GuiRender.inside(mx, my, x, y, w, ROW_H)) {
            String[] positions = {"TOP_CENTER", "TOP_LEFT", "TOP_RIGHT", "BOTTOM_RIGHT", "BOTTOM_LEFT"};
            int idx = 0;
            for (int i = 0; i < positions.length; i++) {
                if (positions[i].equals(cfg.hudPosition)) { idx = i; break; }
            }
            cfg.hudPosition = positions[(idx + 1) % positions.length];
            // Reset custom position when using presets
            cfg.hudX = -1;
            cfg.hudY = -1;
            ConfigManager.save();
            return y + ROW_H;
        }
        y += ROW_H;

        // Auto-Hide slider
        y = checkSliderClick(x, y, w, mx, my, "hudAutoHideSeconds");
        y += ROW_H;

        // Now Playing Toast toggle
        if (GuiRender.inside(mx, my, x, y, w, ROW_H)) {
            int toggleX = x + w - TOGGLE_W - 4;
            int toggleY = y + (ROW_H - TOGGLE_H) / 2;
            if (GuiRender.inside(mx, my, toggleX, toggleY, TOGGLE_W, TOGGLE_H)) {
                cfg.showNowPlayingToast = !cfg.showNowPlayingToast;
                ConfigManager.save();
                return y + ROW_H;
            }
        }
        y += ROW_H;

        // Edit HUD Position button
        y = checkActionClick(x, y, w, mx, my, "editHudPosition");

        return y;
    }

    // ── YouTube click handling ─────────────────────────────────────────────

    private int checkYouTubeClicks(int x, int y, int w, double mx, double my) {
        YouTubeToolManager tools = ServiceManager.getYouTubeToolManager();

        // Tool Status row (no click action)
        y += ROW_H;

        // Install/Reinstall button
        if (tools != null && (tools.getState() == YouTubeToolManager.SetupState.MISSING
                || tools.getState() == YouTubeToolManager.SetupState.ERROR)) {
            y = checkActionClick(x, y, w, mx, my, "installTools");
        } else if (tools != null && tools.getState() == YouTubeToolManager.SetupState.READY) {
            y = checkActionClick(x, y, w, mx, my, "reinstallTools");
        }

        // yt-dlp Path — click to edit
        y = checkPathClick(x, y, w, mx, my, "youtubeYtDlpPath");
        // ffmpeg Path
        y = checkPathClick(x, y, w, mx, my, "youtubeFfmpegPath");
        // Download Timeout slider
        y = checkSliderClick(x, y, w, mx, my, "youtubeDownloadTimeoutSeconds");
        y += ROW_H;
        // Concurrent Fragments slider
        y = checkSliderClick(x, y, w, mx, my, "youtubeDownloadConcurrentFragments");
        y += ROW_H;
        // Cookies File
        y = checkPathClick(x, y, w, mx, my, "youtubeCookiesFile");

        return y;
    }

    // ── Storage click handling ─────────────────────────────────────────────

    private int checkStorageClicks(int x, int y, int w, double mx, double my) {
        // Local Music Folder — Open button
        y = checkFolderClick(x, y, w, mx, my, "openLocalFolder");
        // Downloads Folder — Open button
        y = checkFolderClick(x, y, w, mx, my, "openDownloadsFolder");
        // Cache Limit slider
        y = checkSliderClick(x, y, w, mx, my, "youtubeCacheMaxSizeMb");
        y += ROW_H;
        // Max Cached Tracks slider
        y = checkSliderClick(x, y, w, mx, my, "youtubeCacheMaxTracks");
        y += ROW_H;
        // Clear Cache
        y = checkActionClick(x, y, w, mx, my, "clearCache");

        return y;
    }

    // ── About click handling ───────────────────────────────────────────────

    private int checkAboutClicks(int x, int y, int w, double mx, double my) {
        // Version row (no click)
        y += ROW_H;

        // Reset / Confirm
        if (confirmReset) {
            y = checkActionClick(x, y, w, mx, my, "confirmReset");
            y = checkActionClick(x, y, w, mx, my, "cancelReset");
        } else {
            y = checkActionClick(x, y, w, mx, my, "resetAll");
        }

        return y;
    }

    // ── Generic click helpers ──────────────────────────────────────────────

    private int checkSliderClick(int x, int y, int w, double mx, double my, String field) {
        int sliderX = x + w - SLIDER_W - 4;
        int sliderY = y + (ROW_H - SLIDER_H) / 2;
        if (GuiRender.inside(mx, my, sliderX, sliderY, SLIDER_W, SLIDER_H)) {
            draggingSlider = field;
            updateSliderFromMouse(x, w, mx);
            return y;
        }
        return y;
    }

    private int checkPathClick(int x, int y, int w, double mx, double my, String field) {
        if (GuiRender.inside(mx, my, x, y, w, ROW_H)) {
            // Start editing this path field
            if (!field.equals(editingField)) {
                editingField = field;
                XMusicConfig cfg = ConfigManager.get();
                switch (field) {
                    case "youtubeYtDlpPath": editText = cfg.youtubeYtDlpPath; break;
                    case "youtubeFfmpegPath": editText = cfg.youtubeFfmpegPath; break;
                    case "youtubeCookiesFile": editText = cfg.youtubeCookiesFile; break;
                    default: editText = "";
                }
            }
            return y + ROW_H;
        }
        return y + ROW_H;
    }

    private int checkFolderClick(int x, int y, int w, double mx, double my, String actionId) {
        String btnLabel = "Open Folder";
        Font font = Minecraft.getInstance().font;
        int btnW = font.width(btnLabel) + 12;
        int btnX = x + w - btnW - 4;
        int btnY = y + (ROW_H - BTN_H) / 2;
        if (GuiRender.inside(mx, my, btnX, btnY, btnW, BTN_H)) {
            executeAction(actionId);
            return y + ROW_H;
        }
        return y + ROW_H;
    }

    private int checkActionClick(int x, int y, int w, double mx, double my, String actionId) {
        Font font = Minecraft.getInstance().font;
        // Estimate button width based on action
        String btnLabel = getActionLabel(actionId);
        int btnW = font.width(btnLabel) + 16;
        int btnX = x + w - btnW - 4;
        int btnY = y + (ROW_H - BTN_H) / 2;
        if (GuiRender.inside(mx, my, btnX, btnY, btnW, BTN_H)) {
            executeAction(actionId);
            return y + ROW_H;
        }
        return y + ROW_H;
    }

    private String getActionLabel(String actionId) {
        switch (actionId) {
            case "installTools": return "Install Tools";
            case "reinstallTools": return "Reinstall";
            case "clearCache": return "Clear Cache";
            case "resetAll": return "Reset All Settings";
            case "confirmReset": return "Yes, Reset";
            case "cancelReset": return "Cancel";
            case "editHudPosition": return "Edit HUD Position";
            default: return "Action";
        }
    }

    // ── Action execution ───────────────────────────────────────────────────

    private void executeAction(String actionId) {
        switch (actionId) {
            case "installTools":
            case "reinstallTools": {
                YouTubeToolManager tools = ServiceManager.getYouTubeToolManager();
                if (tools != null) tools.installToolsAsync();
                break;
            }
            case "openLocalFolder": {
                LocalMusicService localService = ServiceManager.getLocalMusic();
                if (localService != null) {
                    XMusic.getPlatform().openFolder(localService.getMusicDirectory());
                }
                break;
            }
            case "openDownloadsFolder": {
                Path dir = DownloadManager.getInstance().getDownloadsDir();
                if (dir != null) XMusic.getPlatform().openFolder(dir);
                break;
            }
            case "clearCache": {
                try {
                    Path cacheDir = XMusic.getPlatform().getGameDir().resolve("xmusic").resolve("cache");
                    if (Files.isDirectory(cacheDir)) {
                        Files.walk(cacheDir)
                                .sorted(java.util.Comparator.reverseOrder())
                                .map(java.nio.file.Path::toFile)
                                .forEach(File -> {
                                    if (!File.equals(cacheDir.toFile())) File.delete();
                                });
                    }
                    XMusic.LOGGER.info("[Settings] Cache cleared.");
                } catch (Exception e) {
                    XMusic.LOGGER.warn("[Settings] Failed to clear cache", e);
                }
                break;
            }
            case "resetAll": {
                confirmReset = true;
                break;
            }
            case "confirmReset": {
                ConfigManager.reset();
                confirmReset = false;
                XMusic.LOGGER.info("[Settings] All settings reset to defaults.");
                break;
            }
            case "cancelReset": {
                confirmReset = false;
                break;
            }
            case "editHudPosition": {
                Minecraft.getInstance().setScreen(new com.codexceed.xmusic.gui.screen.HudEditorScreen());
                break;
            }
        }
    }

    // ── Slider drag ────────────────────────────────────────────────────────

    private void updateSliderFromMouse(int x, int w, double mx) {
        if (draggingSlider == null) return;

        int sliderX = x + w - SLIDER_W - 4;
        float pct = (float) ((mx - sliderX) / SLIDER_W);
        if (pct < 0) pct = 0;
        if (pct > 1) pct = 1;

        XMusicConfig cfg = ConfigManager.get();
        switch (draggingSlider) {
            case "volumeStep":
                cfg.volumeStep = Math.round((0.05f + pct * 0.20f) * 100f) / 100f;
                break;
            case "youtubeDownloadTimeoutSeconds":
                cfg.youtubeDownloadTimeoutSeconds = (int) (60 + pct * 540);
                cfg.youtubeDownloadTimeoutSeconds = Math.round(cfg.youtubeDownloadTimeoutSeconds / 30f) * 30;
                break;
            case "youtubeDownloadConcurrentFragments":
                cfg.youtubeDownloadConcurrentFragments = (int) (1 + pct * 7);
                break;
            case "youtubeCacheMaxSizeMb":
                cfg.youtubeCacheMaxSizeMb = (int) (128 + pct * 1920);
                cfg.youtubeCacheMaxSizeMb = Math.round(cfg.youtubeCacheMaxSizeMb / 64f) * 64;
                break;
            case "youtubeCacheMaxTracks":
                cfg.youtubeCacheMaxTracks = (int) (8 + pct * 56);
                cfg.youtubeCacheMaxTracks = Math.round(cfg.youtubeCacheMaxTracks / 4f) * 4;
                break;
            case "hudAutoHideSeconds":
                cfg.hudAutoHideSeconds = (int) (0 + pct * 30);
                break;
        }
        ConfigManager.save();
    }

    public boolean mouseReleased(GuiFrame frame, double mouseX, double mouseY) {
        if (draggingSlider != null) {
            draggingSlider = null;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(GuiFrame frame, double mouseX, double mouseY) {
        if (draggingSlider != null) {
            int x = frame.contentX() + PAD;
            int w = frame.contentWidth() - PAD * 2;
            updateSliderFromMouse(x, w, mouseX);
            return true;
        }
        return false;
    }

    // ── Scroll ─────────────────────────────────────────────────────────────

    public boolean mouseScrolled(GuiFrame frame, double mouseX, double mouseY, double amount) {
        targetScroll -= amount * 20;
        if (targetScroll < 0) targetScroll = 0;
        if (targetScroll > maxScroll) targetScroll = maxScroll;
        return true;
    }

    // ── Keyboard ───────────────────────────────────────────────────────────

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingField != null) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                commitEdit();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                editingField = null;
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
                if (!editText.isEmpty()) {
                    editText = editText.substring(0, editText.length() - 1);
                }
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (editingField != null) {
            if (codePoint >= 32 && codePoint < 127) {
                editText += codePoint;
                return true;
            }
        }
        return false;
    }

    private void commitEdit() {
        if (editingField == null) return;
        XMusicConfig cfg = ConfigManager.get();
        switch (editingField) {
            case "youtubeYtDlpPath": cfg.youtubeYtDlpPath = editText.trim(); break;
            case "youtubeFfmpegPath": cfg.youtubeFfmpegPath = editText.trim(); break;
            case "youtubeCookiesFile": cfg.youtubeCookiesFile = editText.trim(); break;
        }
        ConfigManager.save();
        editingField = null;
    }
}
