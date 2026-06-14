package com.codexceed.xmusic.gui.component;

import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.download.DownloadEntry;
import com.codexceed.xmusic.download.DownloadManager;
import com.codexceed.xmusic.download.DownloadState;
import com.codexceed.xmusic.gui.layout.GuiFrame;
import com.codexceed.xmusic.gui.render.GuiRender;
import com.codexceed.xmusic.gui.render.IconRenderer;
import com.codexceed.xmusic.gui.theme.GuiTheme;
import com.codexceed.xmusic.player.PlayerFacade;
import com.codexceed.xmusic.player.PlayerState;
import com.codexceed.xmusic.service.ServiceManager;
import com.codexceed.xmusic.service.youtube.YouTubeToolManager;
import com.codexceed.xmusic.source.TrackRef;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Desktop;
import java.util.ArrayList;
import java.util.List;

/**
 * Downloads tab: compact tool status, expandable search, download list with actions.
 */
public final class DownloadsTab {

    private static final int INNER_PAD = 6;
    private static final int ROW_H = 36; // for downloading/failed rows
    private static final int PROGRESS_BAR_H = 4;
    private static final int ACTION_BTN_SIZE = 12;
    private static final int ACTION_BTN_PAD = 2;
    private static final int ACTION_BTN_STEP = ACTION_BTN_SIZE + ACTION_BTN_PAD * 2;
    private static final int ACTION_BTN_GAP = 3;
    private static final int RIGHT_MARGIN = 4;
    private static final int TOOLBAR_H = 18;
    private static final int SEARCH_BAR_H = 16;
    private static final int SEARCH_BTN_SIZE = 14;
    private static final int FILTER_BTN_H = 14;
    private static final int ACTION_BAR_H = 20;

    private final TrackRow trackRow = new TrackRow();

    // ── State ────────────────────────────────────────────────────────────
    private boolean searchExpanded = false;
    private boolean searchFocused = false;
    private String searchQuery = "";
    private DownloadFilter activeFilter = DownloadFilter.ALL;
    private double scrollOffset = 0;

    private enum DownloadFilter {
        ALL, DOWNLOADING, COMPLETED, FAILED
    }

    public void render(GuiGraphics graphics, Font font, GuiFrame frame, int mouseX, int mouseY) {
        int x = frame.contentX();
        int y = frame.contentY();
        int w = frame.contentWidth();
        int h = frame.contentHeight();
        int screenW = frame.x() + frame.width();
        int screenH = frame.y() + frame.height();

        GuiRender.mcPanel(graphics, x, y, w, h);

        int rowX = x + INNER_PAD;
        int rowW = w - INNER_PAD * 2;
        int currentY = y + INNER_PAD;

        // ── Title ────────────────────────────────────────────────────────
        GuiRender.shadowText(graphics, font, "Downloads", rowX, currentY, GuiTheme.ACCENT);
        currentY += 14;

        // ── Setup prompt overlay (only if tools missing and not permanently skipped) ──
        YouTubeToolManager tools = ServiceManager.getYouTubeToolManager();
        if (tools != null && !ConfigManager.get().setupPromptSkipped) {
            YouTubeToolManager.SetupState st = tools.getState();
            boolean showPrompt = st == YouTubeToolManager.SetupState.MISSING
                    || st == YouTubeToolManager.SetupState.ERROR
                    || st == YouTubeToolManager.SetupState.INSTALLING;
            if (showPrompt) {
                renderSetupPrompt(graphics, font, x, y, w, h, mouseX, mouseY, tools);
                return; // Don't render the rest of the tab while prompt is showing
            }
        }

        // Pre-calculate header height for solid background (toolbar + search + filter + action bar)
        int headerStartY = currentY;
        int estimatedHeaderH = TOOLBAR_H + 4;
        if (searchExpanded) estimatedHeaderH += SEARCH_BAR_H + 4;
        estimatedHeaderH += FILTER_BTN_H + 4 + ACTION_BAR_H + 4;

        // ── Toolbar: compact tool status + search + filter ──────────────
        currentY = renderToolbar(graphics, font, rowX, currentY, rowW, mouseX, mouseY, screenW, screenH);
        currentY += 4;

        // ── Search bar (if expanded) ─────────────────────────────────────
        if (searchExpanded) {
            renderSearchBar(graphics, font, rowX, currentY, rowW, mouseX, mouseY);
            currentY += SEARCH_BAR_H + 4;
        }

        // ── Filter buttons ───────────────────────────────────────────────
        currentY = renderFilterBar(graphics, font, rowX, currentY, rowW, mouseX, mouseY);
        currentY += 4;

        // ── Downloads List ───────────────────────────────────────────────
        List<DownloadEntry> entries = getFilteredEntries();

        // Separate active (downloading/failed) from completed entries
        List<DownloadEntry> activeEntries = entries.stream()
                .filter(e -> e.state != DownloadState.COMPLETED)
                .collect(java.util.stream.Collectors.toList());
        List<TrackRef> completedTracks = entries.stream()
                .filter(e -> e.state == DownloadState.COMPLETED)
                .map(e -> e.track)
                .collect(java.util.stream.Collectors.toList());

        if (entries.isEmpty()) {
            if (searchExpanded && !searchQuery.isEmpty()) {
                GuiRender.text(graphics, font, "No results for \"" + searchQuery + "\"", rowX, currentY, GuiTheme.TEXT_MUTED);
            } else {
                GuiRender.text(graphics, font, "No downloads yet.", rowX, currentY, GuiTheme.TEXT_MUTED);
                GuiRender.text(graphics, font, "Click the download button on any track to start.", rowX, currentY + 12, GuiTheme.TEXT_MUTED);
            }
            return;
        }

        // Action bar: Play All + Shuffle for completed downloads
        int btnH = ACTION_BAR_H - 4;
        int btnX = rowX;
        if (!completedTracks.isEmpty()) {
            boolean playAllHover = GuiRender.inside(mouseX, mouseY, btnX, currentY, ToolbarButton.getWidth(font, "Play All"), btnH);
            btnX += ToolbarButton.render(graphics, font, btnX, currentY, btnH, "Play All", IconRenderer::playAll, playAllHover, false) + ToolbarButton.GAP;
            boolean shuffleHover = GuiRender.inside(mouseX, mouseY, btnX, currentY, ToolbarButton.getWidth(font, "Shuffle"), btnH);
            btnX += ToolbarButton.render(graphics, font, btnX, currentY, btnH, "Shuffle", IconRenderer::shuffle, shuffleHover, false) + ToolbarButton.GAP;
        }
        // Track count (right side)
        String countLabel = completedTracks.size() + " completed" + (activeEntries.isEmpty() ? "" : ", " + activeEntries.size() + " active");
        GuiRender.text(graphics, font, countLabel, rowX + rowW - font.width(countLabel) - 4, currentY + 3, GuiTheme.TEXT_MUTED);
        currentY += ACTION_BAR_H + 4;

        // Draw solid background behind pinned header area so scrolling tracks don't show through
        graphics.fill(x, headerStartY, x + w, currentY, GuiTheme.PANEL);

        // Re-render header content on top of the solid background (toolbar, search, filter, action bar)
        int hrY = headerStartY;
        hrY = renderToolbar(graphics, font, rowX, hrY, rowW, mouseX, mouseY, screenW, screenH);
        hrY += 4;
        if (searchExpanded) {
            renderSearchBar(graphics, font, rowX, hrY, rowW, mouseX, mouseY);
            hrY += SEARCH_BAR_H + 4;
        }
        hrY = renderFilterBar(graphics, font, rowX, hrY, rowW, mouseX, mouseY);
        hrY += 4;
        if (!completedTracks.isEmpty()) {
            int rbx = rowX;
            boolean pah = GuiRender.inside(mouseX, mouseY, rbx, hrY, ToolbarButton.getWidth(font, "Play All"), btnH);
            rbx += ToolbarButton.render(graphics, font, rbx, hrY, btnH, "Play All", IconRenderer::playAll, pah, false) + ToolbarButton.GAP;
            boolean sh = GuiRender.inside(mouseX, mouseY, rbx, hrY, ToolbarButton.getWidth(font, "Shuffle"), btnH);
            rbx += ToolbarButton.render(graphics, font, rbx, hrY, btnH, "Shuffle", IconRenderer::shuffle, sh, false) + ToolbarButton.GAP;
        }
        GuiRender.text(graphics, font, countLabel, rowX + rowW - font.width(countLabel) - 4, hrY + 3, GuiTheme.TEXT_MUTED);

        // Calculate total content height: active entries + completed section
        int totalContentH = 0;
        for (DownloadEntry entry : activeEntries) {
            totalContentH += ROW_H + 2;
        }
        if (!completedTracks.isEmpty()) {
            totalContentH += 14; // "Completed" header
            for (TrackRef t : completedTracks) {
                totalContentH += TrackRow.HEIGHT + 2;
            }
        }

        // Scrollable area
        int listH = y + h - currentY - INNER_PAD;
        if (scrollOffset > Math.max(0, totalContentH - listH)) {
            scrollOffset = Math.max(0, totalContentH - listH);
        }

        int drawY = currentY - (int) scrollOffset;
        // Clip region
        graphics.enableScissor(rowX, currentY, rowX + rowW, currentY + listH);

        PlayerFacade facade = PlayerFacade.getInstance();
        TrackRef currentPlaying = facade.snapshot().getCurrentTrack();

        // ── Active downloads (DOWNLOADING / FAILED) ──
        for (DownloadEntry entry : activeEntries) {
            TrackRef track = entry.track;
            DownloadState state = entry.state;
            if (drawY + ROW_H < currentY) { drawY += ROW_H + 2; continue; }
            if (drawY > currentY + listH) break;

            String title = track.getTitle() != null ? track.getTitle() : "Unknown";
            String artist = track.getArtist() != null ? track.getArtist() : "Unknown";
            float progress = entry.progress;

            // Row background
            int rowFill = state == DownloadState.FAILED ? 0xFF1A0A0A : GuiTheme.PANEL_DARK;
            graphics.fill(rowX, drawY, rowX + rowW, drawY + ROW_H, rowFill);
            GuiRender.bevel(graphics, rowX, drawY, rowW, ROW_H, false);
            if (state == DownloadState.FAILED) {
                GuiRender.accentGlow(graphics, rowX, drawY, rowW, ROW_H);
                graphics.fill(rowX, drawY, rowX + rowW, drawY + 1, 0x40FF3B4B);
            }

            // Action buttons area
            int actionsCount = state == DownloadState.DOWNLOADING ? 1 : 0;
            int actionsW = actionsCount > 0 ? ACTION_BTN_STEP : 0;
            int textAreaW = rowW - actionsW - RIGHT_MARGIN - 8;

            // Track info
            int textX = rowX + 4;
            int titleColor = state == DownloadState.FAILED ? GuiTheme.DANGER : GuiTheme.TEXT;
            GuiRender.truncated(graphics, font, title, textX, drawY + 3, textAreaW, titleColor);
            GuiRender.truncated(graphics, font, artist, textX, drawY + 14, textAreaW, GuiTheme.TEXT_MUTED);

            // Progress bar
            int barX = textX;
            int barY = drawY + ROW_H - PROGRESS_BAR_H - 3;
            int barW = textAreaW;
            GuiRender.mcWell(graphics, barX, barY, barW, PROGRESS_BAR_H);

            if (state == DownloadState.DOWNLOADING) {
                int filled = (int)(barW * progress);
                graphics.fill(barX + 1, barY + 1, barX + filled, barY + PROGRESS_BAR_H - 1, GuiTheme.ACCENT);
                if (filled > 2) {
                    graphics.fill(barX + 1, barY - 1, barX + filled + 1, barY, GuiTheme.GLOW_ACCENT);
                }
                String pctStr = (int)(progress * 100) + "%";
                int pctW = font.width(pctStr);
                GuiRender.shadowText(graphics, font, pctStr, barX + barW - pctW - 2, barY - 10, GuiTheme.ACCENT);

                // Cancel button
                int btnY2 = drawY + (ROW_H - ACTION_BTN_STEP) / 2;
                int btnX2 = rowX + rowW - actionsW - RIGHT_MARGIN;
                boolean cancelHover = GuiRender.inside(mouseX, mouseY, btnX2 + ACTION_BTN_PAD, btnY2 + ACTION_BTN_PAD, ACTION_BTN_SIZE, ACTION_BTN_SIZE);
                float pulse = (float)(Math.sin(System.currentTimeMillis() / 300.0) * 0.3 + 0.7);
                int pulseColor = ((int)(0x30 + 0x30 * pulse) << 24) | (GuiTheme.DANGER & 0x00FFFFFF);
                graphics.fill(btnX2, btnY2, btnX2 + ACTION_BTN_STEP, btnY2 + ACTION_BTN_STEP, pulseColor);
                GuiRender.mcButton(graphics, btnX2, btnY2, ACTION_BTN_STEP, ACTION_BTN_STEP, cancelHover, false);
                IconRenderer.clear(graphics, font, btnX2 + ACTION_BTN_PAD, btnY2 + ACTION_BTN_PAD, ACTION_BTN_SIZE, ACTION_BTN_SIZE, cancelHover ? GuiTheme.DANGER : GuiTheme.TEXT_MUTED);
            } else if (state == DownloadState.FAILED) {
                String errStr = entry.error != null ? entry.error : "Failed";
                GuiRender.truncated(graphics, font, errStr, barX, barY - 2, barW, GuiTheme.DANGER);
            }

            drawY += ROW_H + 2;
        }

        // ── Completed downloads (TrackRow) ──
        if (!completedTracks.isEmpty()) {
            // Section header
            if (drawY + 14 >= currentY && drawY < currentY + listH) {
                GuiRender.shadowText(graphics, font, "Completed", rowX, drawY, GuiTheme.ACCENT);
            }
            drawY += 14;

            for (TrackRef track : completedTracks) {
                if (drawY + TrackRow.HEIGHT < currentY) { drawY += TrackRow.HEIGHT + 2; continue; }
                if (drawY > currentY + listH) break;

                boolean isPlaying = currentPlaying != null
                        && currentPlaying.getId().equals(track.getId())
                        && currentPlaying.getSourceId().equals(track.getSourceId());
                boolean isFav = com.codexceed.xmusic.library.LibraryManager.getInstance().isFavorite(track);
                String durationStr = track.getDurationMs() > 0 ? formatDuration(track.getDurationMs()) : "--:--";

                trackRow.render(graphics, font, rowX, drawY, rowW,
                        track.getTitle() != null ? track.getTitle() : "Unknown",
                        track.getArtist() != null ? track.getArtist() : "Unknown",
                        durationStr, isPlaying, false, isFav, track,
                        mouseX, mouseY, screenW, screenH);
                drawY += TrackRow.HEIGHT + 2;
            }
        }

        graphics.disableScissor();

        // Scroll indicator
        if (totalContentH > listH) {
            int scrollBarH = Math.max(12, (int)((float) listH / totalContentH * listH));
            int scrollBarY = currentY + (int)(scrollOffset / totalContentH * listH);
            graphics.fill(rowX + rowW - 2, scrollBarY, rowX + rowW, scrollBarY + scrollBarH, 0x40808080);
        }
    }

    // ── Toolbar: compact tool status + search toggle ────────────────────

    private int renderToolbar(GuiGraphics g, Font f, int x, int y, int w, int mx, int my, int sw, int sh) {
        // Background bar
        g.fill(x, y, x + w, y + TOOLBAR_H, GuiTheme.PANEL_DARK);
        GuiRender.bevel(g, x, y, w, TOOLBAR_H, true);

        int curX = x + 4;
        int textY = y + (TOOLBAR_H - 8) / 2;

        // Tool status chips (compact: name + tick/cross)
        YouTubeToolManager tools = ServiceManager.getYouTubeToolManager();
        if (tools != null) {
            if (tools.getState() == YouTubeToolManager.SetupState.CHECKING) {
                tools.refreshStatusAsync();
            }

            boolean ytReady = tools.hasYtDlp();
            boolean ffReady = tools.hasFfmpeg();
            boolean installing = tools.isInstalling();
            YouTubeToolManager.InstallStep step = tools.getInstallStep();

            // yt-dlp chip
            curX = renderToolChip(g, f, curX, y, "yt-dlp", ytReady, installing && step == YouTubeToolManager.InstallStep.DOWNLOADING_YTDLP);
            curX += 6;

            // ffmpeg chip
            curX = renderToolChip(g, f, curX, y, "ffmpeg", ffReady, installing && step == YouTubeToolManager.InstallStep.DOWNLOADING_FFMPEG);
            curX += 8;

            // Install button if anything missing and not installing
            if ((!ytReady || !ffReady) && !installing) {
                int installW = 56;
                int installH = 16;
                int installY = y + (TOOLBAR_H - installH) / 2;
                boolean installHover = GuiRender.inside(mx, my, curX, installY, installW, installH);
                GuiRender.mcButton(g, curX, installY, installW, installH, installHover, false);
                int iconSz = 10;
                IconRenderer.download(g, f, curX + 3, installY + (installH - iconSz) / 2, iconSz, iconSz, installHover ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);
                GuiRender.shadowText(g, f, "Install", curX + iconSz + 4, installY + (installH - 8) / 2, installHover ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);
                curX += installW + 4;
            }

            // Installing progress — show step label only (no %)
            if (installing) {
                String stepLabel = step.label;
                if (stepLabel.isEmpty()) stepLabel = "Installing...";
                GuiRender.shadowText(g, f, stepLabel, curX, textY, GuiTheme.ACCENT);
                curX += f.width(stepLabel) + 8;
            }

            // Error message (inline, truncated)
            if (tools.getState() == YouTubeToolManager.SetupState.ERROR) {
                String errMsg = tools.getMessage();
                if (errMsg != null && !errMsg.isEmpty()) {
                    int errW = w - (curX - x) - SEARCH_BTN_SIZE - 16;
                    if (errW > 30) {
                        GuiRender.truncated(g, f, errMsg, curX, textY, errW, GuiTheme.DANGER);
                    }
                }
            }
        }

        // Search toggle button (right side, before folder)
        int folderBtnSize = SEARCH_BTN_SIZE;
        int searchBtnX = x + w - SEARCH_BTN_SIZE - folderBtnSize - 8;
        int searchBtnY = y + (TOOLBAR_H - SEARCH_BTN_SIZE) / 2;
        boolean searchHover = GuiRender.inside(mx, my, searchBtnX, searchBtnY, SEARCH_BTN_SIZE, SEARCH_BTN_SIZE);
        if (searchExpanded) {
            g.fill(searchBtnX - 1, searchBtnY - 1, searchBtnX + SEARCH_BTN_SIZE + 1, searchBtnY + SEARCH_BTN_SIZE + 1, GuiTheme.GLOW_ACCENT);
        }
        GuiRender.mcButton(g, searchBtnX, searchBtnY, SEARCH_BTN_SIZE, SEARCH_BTN_SIZE, searchHover, false);
        IconRenderer.search(g, f, searchBtnX + 1, searchBtnY + 1, SEARCH_BTN_SIZE - 2, SEARCH_BTN_SIZE - 2, searchExpanded ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);
        if (searchHover) GuiRender.tooltip(g, f, "Search Downloads", mx, my, sw, sh);

        // Folder button (far right)
        int folderBtnX = x + w - folderBtnSize - 4;
        int folderBtnY = y + (TOOLBAR_H - folderBtnSize) / 2;
        boolean folderHover = GuiRender.inside(mx, my, folderBtnX, folderBtnY, folderBtnSize, folderBtnSize);
        GuiRender.mcButton(g, folderBtnX, folderBtnY, folderBtnSize, folderBtnSize, folderHover, false);
        IconRenderer.folder(g, f, folderBtnX + 1, folderBtnY + 1, folderBtnSize - 2, folderBtnSize - 2, folderHover ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);
        if (folderHover) GuiRender.tooltip(g, f, "Open Downloads Folder", mx, my, sw, sh);

        return y + TOOLBAR_H;
    }

    /** Render a compact tool chip: "name ✓" or "name ✗" with bigger, clearer icons */
    private int renderToolChip(GuiGraphics g, Font f, int x, int barY, String name, boolean ready, boolean activeDownloading) {
        int nameW = f.width(name);
        int iconSz = 11;
        int chipH = TOOLBAR_H - 4;
        int chipY = barY + 2;
        int chipW = nameW + iconSz + 8;

        // Chip background with subtle color tint
        int chipBg;
        if (ready) {
            chipBg = 0x18082008; // subtle green tint
        } else if (activeDownloading) {
            chipBg = 0x180A0A20; // subtle blue tint
        } else {
            chipBg = 0x18200808; // subtle red tint
        }
        g.fill(x, chipY, x + chipW, chipY + chipH, chipBg);
        GuiRender.bevel(g, x, chipY, chipW, chipH, true);

        // Name text
        int textY = chipY + (chipH - 8) / 2;
        GuiRender.shadowText(g, f, name, x + 3, textY, ready ? GuiTheme.TEXT : GuiTheme.TEXT_MUTED);

        // Status icon — bigger and clearer
        int iconX = x + nameW + 5;
        int iconY = chipY + (chipH - iconSz) / 2;
        if (ready) {
            // Green checkmark — use a filled circle + check for visibility
            IconRenderer.checkmark(g, f, iconX, iconY, iconSz, iconSz, 0xFF4CAF50);
        } else if (activeDownloading) {
            // Pulsing download indicator
            float pulse = (float)(Math.sin(System.currentTimeMillis() / 200.0) * 0.3 + 0.7);
            int pulseColor = ((int)(0xFF * pulse) << 24) | (GuiTheme.ACCENT & 0x00FFFFFF);
            IconRenderer.download(g, f, iconX, iconY, iconSz, iconSz, pulseColor);
        } else {
            // Red cross — draw an X using two short lines for clarity
            IconRenderer.cross(g, f, iconX, iconY, iconSz, iconSz, GuiTheme.DANGER);
        }

        return x + chipW;
    }

    // ── Search Bar ──────────────────────────────────────────────────────

    private void renderSearchBar(GuiGraphics g, Font f, int x, int y, int w, int mx, int my) {
        GuiRender.mcWell(g, x, y, w, SEARCH_BAR_H);
        // Focus highlight border
        if (searchFocused) {
            g.fill(x - 1, y - 1, x + w + 1, y, GuiTheme.GLOW_ACCENT);
            g.fill(x - 1, y + SEARCH_BAR_H, x + w + 1, y + SEARCH_BAR_H + 1, GuiTheme.GLOW_ACCENT);
            g.fill(x - 1, y, x, y + SEARCH_BAR_H, GuiTheme.GLOW_ACCENT);
            g.fill(x + w, y, x + w + 1, y + SEARCH_BAR_H, GuiTheme.GLOW_ACCENT);
        }
        String display = searchQuery.isEmpty() ? "Search downloads..." : searchQuery;
        int color = searchQuery.isEmpty() ? GuiTheme.TEXT_MUTED : GuiTheme.TEXT;
        GuiRender.truncated(g, f, display, x + 4, y + (SEARCH_BAR_H - 8) / 2, w - 24, color);
        // Blinking cursor when focused
        if (searchFocused) {
            boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
            if (blink) {
                int cursorX = x + 4 + (searchQuery.isEmpty() ? 0 : f.width(searchQuery));
                g.fill(cursorX, y + 3, cursorX + 1, y + SEARCH_BAR_H - 3, GuiTheme.ACCENT);
            }
        }
        // Clear button
        if (!searchQuery.isEmpty()) {
            int clearX = x + w - 14;
            int clearY = y + (SEARCH_BAR_H - 10) / 2;
            boolean clearHover = GuiRender.inside(mx, my, clearX, clearY, 10, 10);
            IconRenderer.clear(g, f, clearX, clearY, 10, 10, clearHover ? GuiTheme.DANGER : GuiTheme.TEXT_MUTED);
        }
    }

    // ── Filter Bar ──────────────────────────────────────────────────────

    private int renderFilterBar(GuiGraphics g, Font f, int x, int y, int w, int mx, int my) {
        int curX = x;
        DownloadFilter[] filters = DownloadFilter.values();
        for (DownloadFilter filter : filters) {
            String label = filterName(filter);
            int btnW = f.width(label) + 8;
            boolean active = activeFilter == filter;
            boolean hover = GuiRender.inside(mx, my, curX, y, btnW, FILTER_BTN_H);

            if (active) {
                g.fill(curX, y, curX + btnW, y + FILTER_BTN_H, GuiTheme.PANEL_ACTIVE);
                GuiRender.accentGlow(g, curX, y, btnW, FILTER_BTN_H);
            } else {
                GuiRender.mcButton(g, curX, y, btnW, FILTER_BTN_H, hover, false);
            }
            GuiRender.shadowText(g, f, label, curX + 4, y + (FILTER_BTN_H - 8) / 2, active ? GuiTheme.ACCENT : (hover ? GuiTheme.TEXT : GuiTheme.TEXT_MUTED));
            curX += btnW + 3;
        }

        // Download count (right side)
        List<DownloadEntry> all = DownloadManager.getInstance().getEntries();
        String countStr = all.size() + " total";
        int countW = f.width(countStr);
        GuiRender.text(g, f, countStr, x + w - countW - 4, y + (FILTER_BTN_H - 8) / 2, GuiTheme.TEXT_MUTED);

        return y + FILTER_BTN_H;
    }

    private String filterName(DownloadFilter f) {
        return switch (f) {
            case ALL -> "All";
            case DOWNLOADING -> "Active";
            case COMPLETED -> "Done";
            case FAILED -> "Failed";
        };
    }

    private List<DownloadEntry> getFilteredEntries() {
        List<DownloadEntry> all = DownloadManager.getInstance().getEntries();
        List<DownloadEntry> filtered = new ArrayList<>();

        for (DownloadEntry entry : all) {
            // Apply filter
            if (activeFilter != DownloadFilter.ALL) {
                boolean match = switch (activeFilter) {
                    case DOWNLOADING -> entry.state == DownloadState.DOWNLOADING;
                    case COMPLETED -> entry.state == DownloadState.COMPLETED;
                    case FAILED -> entry.state == DownloadState.FAILED;
                    default -> true;
                };
                if (!match) continue;
            }
            // Apply search
            if (searchExpanded && !searchQuery.isEmpty()) {
                String q = searchQuery.toLowerCase();
                String title = entry.track.getTitle() != null ? entry.track.getTitle().toLowerCase() : "";
                String artist = entry.track.getArtist() != null ? entry.track.getArtist().toLowerCase() : "";
                if (!title.contains(q) && !artist.contains(q)) continue;
            }
            filtered.add(entry);
        }
        return filtered;
    }

    // ── Mouse Click ─────────────────────────────────────────────────────

    public boolean mouseClicked(GuiFrame frame, double mouseX, double mouseY, int button) {
        int x = frame.contentX();
        int y = frame.contentY();
        int w = frame.contentWidth();
        int h = frame.contentHeight();

        // ── Setup prompt clicks (highest priority) ──────────────────────
        YouTubeToolManager tools = ServiceManager.getYouTubeToolManager();
        if (tools != null && !ConfigManager.get().setupPromptSkipped) {
            YouTubeToolManager.SetupState st = tools.getState();
            boolean showPrompt = st == YouTubeToolManager.SetupState.MISSING
                    || st == YouTubeToolManager.SetupState.ERROR
                    || st == YouTubeToolManager.SetupState.INSTALLING;
            if (showPrompt) {
                return handleSetupPromptClick(x, y, w, h, mouseX, mouseY, tools);
            }
        }

        int rowX = x + INNER_PAD;
        int rowW = w - INNER_PAD * 2;
        int currentY = y + INNER_PAD + 14; // skip title

        // ── Toolbar clicks ───────────────────────────────────────────────

        // Install button in toolbar
        if (tools != null && (!tools.hasYtDlp() || !tools.hasFfmpeg()) && !tools.isInstalling()) {
            int curX = rowX + 4;
            // Skip yt-dlp chip + gap + ffmpeg chip + gap
            curX += fontWidthChip("yt-dlp") + 6 + fontWidthChip("ffmpeg") + 8;
            int installW = 56;
            int installH = 16;
            int installY = currentY + (TOOLBAR_H - installH) / 2;
            if (GuiRender.inside(mouseX, mouseY, curX, installY, installW, installH)) {
                tools.installToolsAsync();
                return true;
            }
        }

        // Search toggle
        int folderBtnSize = SEARCH_BTN_SIZE;
        int searchBtnX = rowX + rowW - SEARCH_BTN_SIZE - folderBtnSize - 8;
        int searchBtnY = currentY + (TOOLBAR_H - SEARCH_BTN_SIZE) / 2;
        if (GuiRender.inside(mouseX, mouseY, searchBtnX, searchBtnY, SEARCH_BTN_SIZE, SEARCH_BTN_SIZE)) {
            searchExpanded = !searchExpanded;
            if (searchExpanded) { searchFocused = true; }
            else { searchQuery = ""; searchFocused = false; }
            return true;
        }

        // Folder button
        int folderBtnX = rowX + rowW - folderBtnSize - 4;
        int folderBtnY = currentY + (TOOLBAR_H - folderBtnSize) / 2;
        if (GuiRender.inside(mouseX, mouseY, folderBtnX, folderBtnY, folderBtnSize, folderBtnSize)) {
            java.nio.file.Path dir = DownloadManager.getInstance().getDownloadsDir();
            if (dir != null) com.codexceed.xmusic.XMusic.getPlatform().openFolder(dir);
            return true;
        }

        currentY += TOOLBAR_H + 4;

        // ── Search bar clicks ───────────────────────────────────────────
        if (searchExpanded) {
            // Clear button
            if (!searchQuery.isEmpty()) {
                int clearX = rowX + rowW - 14;
                int clearY = currentY + (SEARCH_BAR_H - 10) / 2;
                if (GuiRender.inside(mouseX, mouseY, clearX, clearY, 10, 10)) {
                    searchQuery = "";
                    searchFocused = true;
                    return true;
                }
            }
            // Click on search bar to focus
            if (GuiRender.inside(mouseX, mouseY, rowX, currentY, rowW, SEARCH_BAR_H)) {
                searchFocused = true;
                return true;
            }
            currentY += SEARCH_BAR_H + 4;
        }

        // ── Filter bar clicks ───────────────────────────────────────────
        int filterY = currentY;
        int curX = rowX;
        DownloadFilter[] filters = DownloadFilter.values();
        for (DownloadFilter filter : filters) {
            String label = filterName(filter);
            int btnW = 30 + 8; // approximate
            if (GuiRender.inside(mouseX, mouseY, curX, filterY, btnW, FILTER_BTN_H)) {
                activeFilter = filter;
                scrollOffset = 0;
                searchFocused = false;
                return true;
            }
            curX += btnW + 3;
        }
        currentY += FILTER_BTN_H + 4;

        // ── Action bar clicks (Play All / Shuffle) ──────────────────────
        List<DownloadEntry> entries = getFilteredEntries();
        List<DownloadEntry> activeEntries = entries.stream()
                .filter(e -> e.state != DownloadState.COMPLETED)
                .collect(java.util.stream.Collectors.toList());
        List<TrackRef> completedTracks = entries.stream()
                .filter(e -> e.state == DownloadState.COMPLETED)
                .map(e -> e.track)
                .collect(java.util.stream.Collectors.toList());

        if (!completedTracks.isEmpty()) {
            int abBtnH = ACTION_BAR_H - 4;
            int abBtnX = rowX;
            net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
            // Play All
            int playAllW = ToolbarButton.getWidth(font, "Play All");
            if (GuiRender.inside(mouseX, mouseY, abBtnX, currentY, playAllW, abBtnH)) {
                PlayerFacade.getInstance().playQueue(completedTracks, 0);
                return true;
            }
            abBtnX += playAllW + ToolbarButton.GAP;
            // Shuffle
            int shuffleW = ToolbarButton.getWidth(font, "Shuffle");
            if (GuiRender.inside(mouseX, mouseY, abBtnX, currentY, shuffleW, abBtnH)) {
                java.util.Collections.shuffle(completedTracks);
                PlayerFacade.getInstance().playQueue(completedTracks, 0);
                return true;
            }
        }
        currentY += ACTION_BAR_H + 4;

        // ── Download entry clicks ────────────────────────────────────────
        searchFocused = false;
        int listH = y + h - currentY - INNER_PAD;
        if (listH < 0) listH = 0;
        int drawY = currentY - (int) scrollOffset;

        // Active entries (DOWNLOADING / FAILED)
        for (DownloadEntry entry : activeEntries) {
            TrackRef track = entry.track;
            DownloadState state = entry.state;
            if (drawY + ROW_H < currentY) { drawY += ROW_H + 2; continue; }
            if (drawY > currentY + listH) break;

            if (state == DownloadState.DOWNLOADING) {
                int actionsW = ACTION_BTN_STEP;
                int btnY2 = drawY + (ROW_H - ACTION_BTN_STEP) / 2;
                int btnX2 = rowX + rowW - actionsW - RIGHT_MARGIN;
                if (GuiRender.inside(mouseX, mouseY, btnX2, btnY2, ACTION_BTN_STEP, ACTION_BTN_STEP)) {
                    DownloadManager.getInstance().cancel(track);
                    return true;
                }
            }

            // Click on row
            if (GuiRender.inside(mouseX, mouseY, rowX, drawY, rowW, ROW_H)) {
                if (state == DownloadState.FAILED) {
                    DownloadManager.getInstance().download(track);
                    return true;
                }
            }

            drawY += ROW_H + 2;
        }

        // Completed section
        if (!completedTracks.isEmpty()) {
            drawY += 14; // section header

            for (TrackRef track : completedTracks) {
                if (drawY + TrackRow.HEIGHT < currentY) { drawY += TrackRow.HEIGHT + 2; continue; }
                if (drawY > currentY + listH) break;

                // Heart button click
                if (TrackRow.isHeartClicked(rowX, drawY, rowW, mouseX, mouseY)) {
                    com.codexceed.xmusic.library.LibraryManager.getInstance().toggleFavorite(track);
                    return true;
                }
                // Download button click (already downloaded — no-op)
                if (TrackRow.isDownloadClicked(rowX, drawY, rowW, mouseX, mouseY)) {
                    return true;
                }
                // Row click — play the track
                if (GuiRender.inside(mouseX, mouseY, rowX, drawY, rowW, TrackRow.HEIGHT)) {
                    PlayerFacade.getInstance().playQueue(java.util.Collections.singletonList(track), 0);
                    return true;
                }
                drawY += TrackRow.HEIGHT + 2;
            }
        }
        return false;
    }

    /** Approximate chip width for click detection (no font available) */
    private int fontWidthChip(String name) {
        // Approximate: each char ~6px + icon 11px + padding 8px
        return name.length() * 6 + 11 + 8;
    }

    // ── Scroll ──────────────────────────────────────────────────────────

    public boolean mouseScrolled(GuiFrame frame, double mouseX, double mouseY, double amount) {
        scrollOffset -= amount * 20;
        if (scrollOffset < 0) scrollOffset = 0;
        // Upper bound is clamped during render via totalContentH - listH
        return true;
    }

    // ── Key Input ───────────────────────────────────────────────────────

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchFocused) {
            if (keyCode == 256) { // ESC
                searchFocused = false;
                return true;
            }
            if (keyCode == 259) { // Backspace
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                }
                return true;
            }
            // Consume Enter to prevent it from playing music
            if (keyCode == 257 || keyCode == 335) { // Enter / KP Enter
                return true;
            }
            return true; // consume all keys while search focused
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (searchFocused) {
            if (Character.isLetterOrDigit(codePoint) || codePoint == ' ' || codePoint == '_' || codePoint == '-') {
                if (searchQuery.length() < 32) {
                    searchQuery += codePoint;
                }
            }
            return true;
        }
        return false;
    }

    private static String formatDuration(long ms) {
        long sec = ms / 1000;
        long min = sec / 60;
        sec %= 60;
        if (min >= 60) {
            long hrs = min / 60;
            min %= 60;
            return hrs + ":" + (min < 10 ? "0" : "") + min + ":" + (sec < 10 ? "0" : "") + sec;
        }
        return min + ":" + (sec < 10 ? "0" : "") + sec;
    }

    // ── Setup Prompt (Downloads tab only) ────────────────────────────────

    private void renderSetupPrompt(GuiGraphics g, Font f, int x, int y, int w, int h, int mx, int my, YouTubeToolManager tools) {
        boolean ytReady = tools.hasYtDlp();
        boolean ffReady = tools.hasFfmpeg();
        boolean installing = tools.isInstalling();
        boolean error = tools.getState() == YouTubeToolManager.SetupState.ERROR;

        // Dialog dimensions — centered in content area
        int dialogW = 220;
        int dialogH = 100;
        int cx = x + (w - dialogW) / 2;
        int cy = y + (h - dialogH) / 2;

        // Push z-offset so dialog renders above everything
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);

        // Dim backdrop behind dialog
        g.fill(x, y, x + w, y + h, 0x60000000);

        // Dialog panel
        g.fill(cx, cy, cx + dialogW, cy + dialogH, GuiTheme.PANEL);
        GuiRender.bevel(g, cx, cy, dialogW, dialogH, false);
        // Accent top border
        g.fill(cx + 1, cy, cx + dialogW - 1, cy + 2, GuiTheme.ACCENT);

        int drawY = cy + 8;

        // Title
        String title = installing ? "Setting Up..." : (error ? "Setup Error" : "Setup Required");
        GuiRender.shadowText(g, f, title, cx + (dialogW - f.width(title)) / 2, drawY, GuiTheme.TEXT);
        drawY += 14;

        // Tool status rows
        int rowX = cx + 16;
        int iconSz = 13;

        // yt-dlp row
        renderToolRow(g, f, rowX, drawY, dialogW - 32, "yt-dlp", ytReady,
                installing && tools.getInstallStep() == YouTubeToolManager.InstallStep.DOWNLOADING_YTDLP);
        drawY += 20;

        // ffmpeg row
        renderToolRow(g, f, rowX, drawY, dialogW - 32, "ffmpeg", ffReady,
                installing && tools.getInstallStep() == YouTubeToolManager.InstallStep.DOWNLOADING_FFMPEG);
        drawY += 22;

        // Install step message
        if (installing) {
            String stepLabel = tools.getInstallStep().label;
            if (stepLabel.isEmpty()) stepLabel = "Installing...";
            GuiRender.shadowText(g, f, stepLabel, cx + (dialogW - f.width(stepLabel)) / 2, drawY, GuiTheme.ACCENT);
        } else if (error) {
            String errMsg = tools.getMessage();
            if (errMsg != null && !errMsg.isEmpty()) {
                GuiRender.truncated(g, f, errMsg, cx + 10, drawY, dialogW - 20, GuiTheme.DANGER);
            }
        }

        // Install button (only when not installing and tools missing)
        if (!installing && (!ytReady || !ffReady)) {
            int btnW = 90;
            int btnH = 18;
            int btnX = cx + (dialogW - btnW) / 2;
            int btnY = cy + dialogH - btnH - 6;
            boolean btnHover = GuiRender.inside(mx, my, btnX, btnY, btnW, btnH);
            GuiRender.mcButton(g, btnX, btnY, btnW, btnH, btnHover, false);
            int dlIcon = 10;
            IconRenderer.download(g, f, btnX + 6, btnY + (btnH - dlIcon) / 2, dlIcon, dlIcon, btnHover ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);
            GuiRender.shadowText(g, f, "Install", btnX + dlIcon + 8, btnY + (btnH - 8) / 2, btnHover ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);
        }

        // Skip link (only when not installing) — permanently dismisses via config
        if (!installing) {
            int skipW = 40;
            int skipH = 10;
            int skipX = cx + dialogW - skipW - 8;
            int skipY = cy + dialogH - skipH - 4;
            boolean skipHover = GuiRender.inside(mx, my, skipX, skipY, skipW, skipH);
            GuiRender.shadowText(g, f, "Skip", skipX, skipY, skipHover ? GuiTheme.TEXT : GuiTheme.TEXT_MUTED);
        }

        g.pose().popPose();
    }

    private void renderToolRow(GuiGraphics g, Font f, int x, int y, int maxW, String name, boolean ready, boolean activeDownloading) {
        int iconSz = 13;
        if (ready) {
            IconRenderer.checkmark(g, f, x, y, iconSz, iconSz, 0xFF4CAF50);
        } else if (activeDownloading) {
            float pulse = (float)(Math.sin(System.currentTimeMillis() / 200.0) * 0.3 + 0.7);
            int pulseColor = ((int)(0xFF * pulse) << 24) | (GuiTheme.ACCENT & 0x00FFFFFF);
            IconRenderer.download(g, f, x, y, iconSz, iconSz, pulseColor);
        } else {
            IconRenderer.cross(g, f, x, y, iconSz, iconSz, GuiTheme.DANGER);
        }
        GuiRender.shadowText(g, f, name, x + iconSz + 4, y + 2, ready ? GuiTheme.TEXT : GuiTheme.TEXT_MUTED);
        String status = ready ? "Installed" : (activeDownloading ? "Downloading..." : "Not found");
        int statusW = f.width(status);
        GuiRender.shadowText(g, f, status, x + maxW - statusW, y + 2, ready ? 0xFF4CAF50 : (activeDownloading ? GuiTheme.ACCENT : GuiTheme.DANGER));
    }

    private boolean handleSetupPromptClick(int x, int y, int w, int h, double mouseX, double mouseY, YouTubeToolManager tools) {
        int dialogW = 220;
        int dialogH = 100;
        int cx = x + (w - dialogW) / 2;
        int cy = y + (h - dialogH) / 2;
        int mx = (int) mouseX;
        int my = (int) mouseY;

        // Click inside dialog area — consume the click
        if (!GuiRender.inside(mx, my, cx, cy, dialogW, dialogH)) {
            return false;
        }

        boolean ytReady = tools.hasYtDlp();
        boolean ffReady = tools.hasFfmpeg();
        boolean installing = tools.isInstalling();

        // Install button click
        if (!installing && (!ytReady || !ffReady)) {
            int btnW = 90;
            int btnH = 18;
            int btnX = cx + (dialogW - btnW) / 2;
            int btnY = cy + dialogH - btnH - 6;
            if (GuiRender.inside(mx, my, btnX, btnY, btnW, btnH)) {
                tools.installToolsAsync();
                return true;
            }
        }

        // Skip link click — permanently dismiss via config
        if (!installing) {
            int skipW = 40;
            int skipH = 10;
            int skipX = cx + dialogW - skipW - 8;
            int skipY = cy + dialogH - skipH - 4;
            if (GuiRender.inside(mx, my, skipX, skipY, skipW, skipH)) {
                ConfigManager.get().setupPromptSkipped = true;
                ConfigManager.save();
                return true;
            }
        }

        // Any click inside dialog consumes the event (prevent passthrough)
        return true;
    }
}
