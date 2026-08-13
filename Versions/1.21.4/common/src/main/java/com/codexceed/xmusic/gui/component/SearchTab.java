package com.codexceed.xmusic.gui.component;

import com.codexceed.xmusic.download.DownloadManager;
import com.codexceed.xmusic.download.DownloadState;
import com.codexceed.xmusic.gui.layout.GuiFrame;
import com.codexceed.xmusic.gui.render.GuiRender;
import com.codexceed.xmusic.gui.render.HoverTracker;
import com.codexceed.xmusic.gui.render.IconRenderer;
import com.codexceed.xmusic.gui.theme.GuiTheme;
import com.codexceed.xmusic.library.LibraryManager;
import com.codexceed.xmusic.player.PlayerFacade;
import com.codexceed.xmusic.service.ServiceManager;
import com.codexceed.xmusic.source.TrackRef;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Search tab — full-featured search with keyboard nav, queue, history, and more.
 */
public final class SearchTab {

    private final TrackRow trackRow = new TrackRow();
    private static String query = "";
    private static boolean queryFocused = false;
    private static List<TrackRef> searchResults = new ArrayList<>();
    private static boolean isSearching = false;
    private static String searchError = null;
    private static double scrollOffset = 0;
    public static boolean isYoutube = true;

    // Keyboard navigation
    private static int selectedIndex = -1;

    private String pendingTooltip = null;

    // URL input state
    private boolean urlInputOpen = false;
    private String urlInput = "";
    private boolean urlLoading = false;
    private String urlError = null;

    // Search history (deduplicated, most recent first)
    private final List<String> searchHistory = new ArrayList<>();
    private boolean searchHistoryOpen = false;
    private static final int MAX_SEARCH_HISTORY = 10;

    public SearchTab() {
        this.searchHistory.clear();
        this.searchHistory.addAll(com.codexceed.xmusic.config.ConfigManager.get().searchHistory);
    }

    // Recently played (from PlayerFacade)
    private boolean recentlyPlayedOpen = false;

    // Duration filter
    private boolean durationFilterOn = false;
    private static final long DURATION_FILTER_MS = 10 * 60 * 1000L; // 10 min



    // Playlist context popup (right-click on track)
    private TrackRef contextTrack = null;
    private int contextX = 0;
    private int contextY = 0;

    // UI Layout Constants
    private static final int SEARCH_BAR_HEIGHT = 26;
    private static final int URL_BTN_WIDTH = 26;
    private static final int URL_INPUT_HEIGHT = 24;
    private static final int PASTE_BTN_WIDTH = 28;
    private static final int CLEAR_BTN_WIDTH = 18;
    private static final int ROW_SPACING = 2;
    private static final int ACTION_BAR_HEIGHT = 22;
    private static final int SECTION_GAP = 6;
    private static final int INNER_PAD = 6;

    // YouTube URL patterns
    private static final Pattern YT_VIDEO_ID = Pattern.compile(
            "(?:v=|/v/|/embed/|/shorts/|youtu\\.be/|/watch\\?.*v=)([a-zA-Z0-9_-]{11})"
    );

    // ── Render ────────────────────────────────────────────────────────────

    public void render(GuiGraphics graphics, Font font, GuiFrame frame, int mouseX, int mouseY) {
        int x = frame.contentX();
        int y = frame.contentY();
        int w = frame.contentWidth();
        int h = frame.contentHeight();

        boolean compact = frame.compact();
        int scrollY = compact ? (int) scrollOffset : 0;

        // Scissor for compact mode
        if (compact) {
            graphics.enableScissor(x, y, x + w, y + h);
        }

        // 1. Search Bar Header
        int searchBarY = y + INNER_PAD - scrollY;
        int searchBarW = w - INNER_PAD * 2;
        int searchBarX = x + INNER_PAD;


        int toggleWidth = compact ? 44 : 140;

        int urlBtnSpace = isYoutube ? URL_BTN_WIDTH + 4 : 0;
        int inputW = searchBarW - toggleWidth - 8 - urlBtnSpace - CLEAR_BTN_WIDTH - 4;
        GuiRender.mcWell(graphics, searchBarX, searchBarY, inputW, SEARCH_BAR_HEIGHT);
        // Focus highlight border
        if (queryFocused) {
            GuiRender.glowRect(graphics, searchBarX, searchBarY, inputW, SEARCH_BAR_HEIGHT);
        }

        // Query text or placeholder
        String placeholder = "Type to search...";
        String displayText = query.isEmpty() ? placeholder : query;
        int textColor = query.isEmpty() ? GuiTheme.TEXT_MUTED : GuiTheme.TEXT;
        GuiRender.shadowText(graphics, font, displayText, searchBarX + 8, searchBarY + 9, textColor);
        // Blinking cursor when focused
        if (queryFocused) {
            boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
            if (blink) {
                int cursorX = searchBarX + 8 + (query.isEmpty() ? 0 : font.width(query));
                graphics.fill(cursorX, searchBarY + 5, cursorX + 1, searchBarY + SEARCH_BAR_HEIGHT - 5, GuiTheme.ACCENT);
            }
        }

        // Clear button (X) — only when query is not empty
        int clearBtnX = searchBarX + inputW + 2;
        if (!query.isEmpty() && !urlInputOpen) {
            boolean clearHover = GuiRender.inside(mouseX, mouseY, clearBtnX, searchBarY + 3, CLEAR_BTN_WIDTH, SEARCH_BAR_HEIGHT - 6);
            GuiRender.mcButton(graphics, clearBtnX, searchBarY + 3, CLEAR_BTN_WIDTH, SEARCH_BAR_HEIGHT - 6, clearHover, false);
            IconRenderer.clear(graphics, font, clearBtnX, searchBarY + 3, CLEAR_BTN_WIDTH, SEARCH_BAR_HEIGHT - 6, clearHover ? GuiTheme.DANGER : GuiTheme.TEXT_MUTED);
            if (clearHover) {
                pendingTooltip = "Clear";
            }
        }

        // URL button
        int urlBtnX = clearBtnX + CLEAR_BTN_WIDTH + 4;
        if (isYoutube) {
            boolean hover = GuiRender.inside(mouseX, mouseY, urlBtnX, searchBarY, URL_BTN_WIDTH, SEARCH_BAR_HEIGHT);
            GuiRender.mcButton(graphics, urlBtnX, searchBarY, URL_BTN_WIDTH, SEARCH_BAR_HEIGHT, hover, urlInputOpen);
            IconRenderer.url(graphics, font, urlBtnX, searchBarY, URL_BTN_WIDTH, SEARCH_BAR_HEIGHT,
                    urlInputOpen ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);
            if (hover) {
                pendingTooltip = "Paste URL";
            }
        }

        // Toggle button
        int toggleX = searchBarX + searchBarW - toggleWidth;
        renderSourceToggle(graphics, font, toggleX, searchBarY + 4, toggleWidth, compact);

        // URL input row
        int listY = searchBarY + SEARCH_BAR_HEIGHT + SECTION_GAP;
        if (urlInputOpen) {
            renderUrlInput(graphics, font, searchBarX, listY, searchBarW, mouseX, mouseY, frame);
            listY += URL_INPUT_HEIGHT + SECTION_GAP;
        }

        // Action bar
        renderActionBar(graphics, font, searchBarX, listY, searchBarW, mouseX, mouseY, frame);
        listY += ACTION_BAR_HEIGHT + SECTION_GAP;

        // Search history dropdown
        if (searchHistoryOpen) {
            renderSearchHistory(graphics, font, searchBarX, listY, searchBarW, mouseX, mouseY);
            if (compact) graphics.disableScissor();
            return; // don't render results while history is open
        }

        // Recently played dropdown
        if (recentlyPlayedOpen) {
            renderRecentlyPlayed(graphics, font, x, searchBarX, listY, searchBarW, h, mouseX, mouseY, frame);
            if (compact) graphics.disableScissor();
            return;
        }

        // Results area
        int listH = h - (listY - y) - INNER_PAD;
        int listW = searchBarW;

        // Apply duration filter
        List<TrackRef> displayResults = durationFilterOn ? filterByDuration(searchResults) : searchResults;

        int scissorY1 = compact ? y : listY;
        int scissorY2 = compact ? y + h : listY + listH;

        if (isSearching) {
            if (!compact) graphics.enableScissor(x, scissorY1, x + w, scissorY2);
            GuiRender.centeredText(graphics, font, "Searching...", x + w / 2, listY + 20, GuiTheme.TEXT_MUTED);
        } else if (searchError != null) {
            if (!compact) graphics.enableScissor(x, scissorY1, x + w, scissorY2);
            GuiRender.centeredText(graphics, font, "Search failed", x + w / 2, listY + 20, GuiTheme.DANGER);
            GuiRender.centeredText(graphics, font, searchError, x + w / 2, listY + 36, GuiTheme.TEXT_MUTED);
        } else if (!query.isEmpty() && searchResults.isEmpty()) {
            if (!compact) graphics.enableScissor(x, scissorY1, x + w, scissorY2);
            GuiRender.centeredText(graphics, font, "Press Enter to search for '" + query + "'", x + w / 2, listY + 20, GuiTheme.TEXT_SOFT);
        } else if (searchResults.isEmpty()) {
            if (!compact) graphics.enableScissor(x, scissorY1, x + w, scissorY2);
            GuiRender.centeredText(graphics, font, "No results. Try a different query.", x + w / 2, listY + 20, GuiTheme.TEXT_MUTED);
        } else {
            if (!compact) graphics.enableScissor(x, scissorY1, x + w, scissorY2);

            int currentY = listY - (compact ? 0 : (int) scrollOffset);
            for (int i = 0; i < displayResults.size(); i++) {
                TrackRef track = displayResults.get(i);

                if (currentY + TrackRow.HEIGHT > scissorY1 && currentY < scissorY2) {
                    boolean isPlaying = isTrackPlaying(track);
                    boolean isSelected = (i == selectedIndex);
                    boolean isFav = LibraryManager.getInstance().isFavorite(track);

                    String durationStr = formatDuration(track.getDurationMs());
                    trackRow.render(graphics, font, searchBarX, currentY, listW,
                            track.getTitle(), track.getArtist(), durationStr, isPlaying, isSelected, isFav, track,
                            mouseX, mouseY, frame.x() + frame.width(), frame.y() + frame.height());
                }
                currentY += TrackRow.HEIGHT + ROW_SPACING;
            }
        }

        if (compact) {
            graphics.disableScissor();
        } else {
            graphics.disableScissor();
        }

        // Render tooltips outside scissor box
        if (pendingTooltip != null) {
            GuiRender.tooltip(graphics, font, pendingTooltip, mouseX, mouseY, frame.x() + frame.width(), frame.y() + frame.height());
            pendingTooltip = null;
        }

        // Context popup (add to playlist / add to queue)
        if (contextTrack != null) {
            renderContextPopup(graphics, font, frame);
        }
    }

    // ── Sub-Renderers ────────────────────────────────────────────────────

    private void renderUrlInput(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY, GuiFrame frame) {
        int urlTextW = w - PASTE_BTN_WIDTH - 4;
        GuiRender.mcWell(graphics, x, y, urlTextW, URL_INPUT_HEIGHT);
        graphics.fill(x, y, x + 1, y + URL_INPUT_HEIGHT, GuiTheme.ACCENT); // accent left border

        if (urlLoading) {
            GuiRender.shadowText(graphics, font, "Loading...", x + 8, y + 8, GuiTheme.ACCENT);
        } else if (urlError != null) {
            GuiRender.shadowText(graphics, font, urlError, x + 8, y + 8, GuiTheme.DANGER);
        } else {
            String urlPlaceholder = "Paste YouTube URL or video ID...";
            String urlDisplay = urlInput.isEmpty() ? urlPlaceholder : urlInput + "_";
            int urlColor = urlInput.isEmpty() ? GuiTheme.TEXT_MUTED : GuiTheme.TEXT;
            int maxChars = urlTextW / 7;
            if (urlDisplay.length() > maxChars) {
                urlDisplay = "..." + urlDisplay.substring(urlDisplay.length() - maxChars + 3);
            }
            GuiRender.shadowText(graphics, font, urlDisplay, x + 8, y + 8, urlColor);
        }

        // Paste button: MC style
        int pasteBtnX = x + w - PASTE_BTN_WIDTH;
        boolean pasteHover = GuiRender.inside(mouseX, mouseY, pasteBtnX, y, PASTE_BTN_WIDTH, URL_INPUT_HEIGHT);
        GuiRender.mcButton(graphics, pasteBtnX, y, PASTE_BTN_WIDTH, URL_INPUT_HEIGHT, pasteHover, false);
        IconRenderer.paste(graphics, font, pasteBtnX, y, PASTE_BTN_WIDTH, URL_INPUT_HEIGHT, pasteHover ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);

        // Tooltip for paste button
        if (pasteHover) {
            pendingTooltip = "Paste URL";
        }
    }

    private void renderActionBar(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY, GuiFrame frame) {
        int btnX = x;
        int btnH = ACTION_BAR_HEIGHT - 4;

        // Play All — auto-sized
        boolean playAllHover = GuiRender.inside(mouseX, mouseY, btnX, y, ToolbarButton.getWidth(font, "Play All"), btnH);
        btnX += ToolbarButton.render(graphics, font, btnX, y, btnH, "Play All", IconRenderer::playAll, playAllHover, false) + ToolbarButton.GAP;

        // Shuffle — auto-sized
        boolean shuffleHover = GuiRender.inside(mouseX, mouseY, btnX, y, ToolbarButton.getWidth(font, "Shuffle"), btnH);
        btnX += ToolbarButton.render(graphics, font, btnX, y, btnH, "Shuffle", IconRenderer::shuffle, shuffleHover, false) + ToolbarButton.GAP;

        // Duration Filter toggle — auto-sized
        boolean durHover = GuiRender.inside(mouseX, mouseY, btnX, y, ToolbarButton.getWidth(font, "10m"), btnH);
        btnX += ToolbarButton.render(graphics, font, btnX, y, btnH, "10m", IconRenderer::durationFilter, durHover, durationFilterOn) + ToolbarButton.GAP;

        // Search History — icon only
        boolean histHover = GuiRender.inside(mouseX, mouseY, btnX, y, ToolbarButton.getIconWidth(), btnH);
        btnX += ToolbarButton.renderIconOnly(graphics, font, btnX, y, btnH, IconRenderer::history, histHover, searchHistoryOpen) + ToolbarButton.GAP;

        // Recently Played — icon only
        boolean recentHover = GuiRender.inside(mouseX, mouseY, btnX, y, ToolbarButton.getIconWidth(), btnH);
        btnX += ToolbarButton.renderIconOnly(graphics, font, btnX, y, btnH, IconRenderer::recent, recentHover, recentlyPlayedOpen) + ToolbarButton.GAP;

        // Tooltips for action bar buttons
        String actionTooltip = null;
        if (playAllHover) actionTooltip = "Play All";
        else if (shuffleHover) actionTooltip = "Shuffle";
        else if (durHover) actionTooltip = "Duration Filter";
        else if (histHover) actionTooltip = "Search History";
        else if (recentHover) actionTooltip = "Recently Played";
        if (actionTooltip != null) {
            pendingTooltip = actionTooltip;
        }
    }

    private void renderSearchHistory(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        List<String> history = new ArrayList<>(searchHistory);
        if (history.isEmpty()) {
            GuiRender.centeredText(graphics, font, "No search history yet", x + w / 2, y + 20, GuiTheme.TEXT_MUTED);
            return;
        }
        for (int i = 0; i < history.size(); i++) {
            String entry = history.get(i);
            boolean hover = GuiRender.inside(mouseX, mouseY, x, y + i * 18, w, 18);
            int bg = hover ? GuiTheme.PANEL_ACTIVE : GuiTheme.PANEL_DARK;
            graphics.fill(x, y + i * 18, x + w, y + i * 18 + 18, bg);
            GuiRender.truncated(graphics, font, entry, x + 8, y + i * 18 + 5, w - 16, hover ? GuiTheme.TEXT : GuiTheme.TEXT_MUTED);
        }
    }

    private void renderRecentlyPlayed(GuiGraphics graphics, Font font, int clipX, int x, int y, int w, int h, int mouseX, int mouseY, GuiFrame frame) {
        List<TrackRef> recent = PlayerFacade.getInstance().getPlayHistory();
        // Show most recent first
        List<TrackRef> reversed = new ArrayList<>();
        for (int i = recent.size() - 1; i >= 0; i--) reversed.add(recent.get(i));

        if (reversed.isEmpty()) {
            GuiRender.centeredText(graphics, font, "No recently played tracks", x + w / 2, y + 20, GuiTheme.TEXT_MUTED);
            return;
        }

        int maxShow = Math.min(reversed.size(), 15);
        int minY = frame.contentY();
        int maxY = frame.contentY() + frame.contentHeight();
        graphics.enableScissor(clipX, Math.max(minY, y), clipX + w + 16, Math.min(maxY, y + h));
        for (int i = 0; i < maxShow; i++) {
            TrackRef track = reversed.get(i);
            int rowY = y + i * (TrackRow.HEIGHT + ROW_SPACING);
            boolean hover = GuiRender.inside(mouseX, mouseY, x, rowY, w, TrackRow.HEIGHT);
            boolean isPlaying = isTrackPlaying(track);
            boolean isFav = LibraryManager.getInstance().isFavorite(track);
            String durationStr = formatDuration(track.getDurationMs());
            trackRow.render(graphics, font, x, rowY, w,
                    track.getTitle(), track.getArtist(), durationStr, isPlaying, hover, isFav, track,
                    mouseX, mouseY, frame.x() + frame.width(), frame.y() + frame.height());
        }
        graphics.disableScissor();
    }

    // ── Mouse ─────────────────────────────────────────────────────────────

    public boolean mouseClicked(GuiFrame frame, double mouseX, double mouseY, int button) {
        // Handle context popup first
        if (contextTrack != null) {
            return clickContextPopup(mouseX, mouseY);
        }

        int x = frame.contentX();
        int y = frame.contentY();
        int w = frame.contentWidth();
        int h = frame.contentHeight();

        if (!GuiRender.inside(mouseX, mouseY, x, y, w, h)) {
            return false;
        }

        boolean compact = frame.compact();
        int scrollY = compact ? (int) scrollOffset : 0;

        int searchBarY = y + INNER_PAD - scrollY;
        int searchBarW = w - INNER_PAD * 2;
        int searchBarX = x + INNER_PAD;

        int toggleWidth = compact ? 44 : 140;
        int urlBtnSpace = isYoutube ? URL_BTN_WIDTH + 4 : 0;
        int inputW = searchBarW - toggleWidth - 8 - urlBtnSpace - CLEAR_BTN_WIDTH - 4;

        // Clear button
        if (!query.isEmpty() && !urlInputOpen) {
            int clearBtnX = searchBarX + inputW + 2;
            if (GuiRender.inside(mouseX, mouseY, clearBtnX, searchBarY + 3, CLEAR_BTN_WIDTH, SEARCH_BAR_HEIGHT - 6)) {
                query = "";
                searchResults.clear();
                selectedIndex = -1;
                queryFocused = true;
                return true;
            }
        }

        // Click on search bar input area to focus
        {
            if (GuiRender.inside(mouseX, mouseY, searchBarX, searchBarY, inputW, SEARCH_BAR_HEIGHT)) {
                queryFocused = true;
                selectedIndex = -1;
                return true;
            }
        }

        // URL button
        if (isYoutube) {
            int clearBtnX = searchBarX + inputW + 2;
            int urlBtnX = clearBtnX + CLEAR_BTN_WIDTH + 4;
            if (GuiRender.inside(mouseX, mouseY, urlBtnX, searchBarY, URL_BTN_WIDTH, SEARCH_BAR_HEIGHT)) {
                urlInputOpen = !urlInputOpen;
                if (!urlInputOpen) { urlInput = ""; urlError = null; }
                return true;
            }
        }

        // Paste button in URL input
        if (urlInputOpen && isYoutube) {
            int urlInputY = searchBarY + SEARCH_BAR_HEIGHT + SECTION_GAP;
            int pasteBtnX = searchBarX + searchBarW - PASTE_BTN_WIDTH;
            if (GuiRender.inside(mouseX, mouseY, pasteBtnX, urlInputY, PASTE_BTN_WIDTH, URL_INPUT_HEIGHT)) {
                String clip = getClipboard();
                if (clip != null && !clip.isEmpty()) { urlInput = clip; urlError = null; }
                return true;
            }
        }

        // Toggle
        int toggleX = searchBarX + searchBarW - toggleWidth;
        if (GuiRender.inside(mouseX, mouseY, toggleX, searchBarY + 4, toggleWidth, 18)) {
            isYoutube = !isYoutube;
            query = "";
            searchResults.clear(); scrollOffset = 0; selectedIndex = -1;
            urlInputOpen = false; urlInput = ""; urlError = null;
            searchHistoryOpen = false; recentlyPlayedOpen = false;
            return true;
        }

        // Action bar buttons
        queryFocused = false;
        int actionY = searchBarY + SEARCH_BAR_HEIGHT + SECTION_GAP;
        if (urlInputOpen) actionY += URL_INPUT_HEIGHT + SECTION_GAP;
        int btnX = searchBarX;
        int btnH = ACTION_BAR_HEIGHT - 4;
        Font font = Minecraft.getInstance().font;

        // Play All
        if (ToolbarButton.isClicked(font, "Play All", btnX, actionY, btnH, mouseX, mouseY) && !searchResults.isEmpty()) {
            PlayerFacade.getInstance().playQueue(searchResults, 0);
            return true;
        }
        btnX += ToolbarButton.getWidth(font, "Play All") + ToolbarButton.GAP;

        // Shuffle
        if (ToolbarButton.isClicked(font, "Shuffle", btnX, actionY, btnH, mouseX, mouseY) && !searchResults.isEmpty()) {
            List<TrackRef> shuffled = new ArrayList<>(searchResults);
            Collections.shuffle(shuffled);
            PlayerFacade.getInstance().playQueue(shuffled, 0);
            return true;
        }
        btnX += ToolbarButton.getWidth(font, "Shuffle") + ToolbarButton.GAP;

        // Duration filter
        if (ToolbarButton.isClicked(font, "10m", btnX, actionY, btnH, mouseX, mouseY)) {
            durationFilterOn = !durationFilterOn;
            return true;
        }
        btnX += ToolbarButton.getWidth(font, "10m") + ToolbarButton.GAP;

        // Search History
        if (ToolbarButton.isIconClicked(btnX, actionY, btnH, mouseX, mouseY)) {
            searchHistoryOpen = !searchHistoryOpen;
            recentlyPlayedOpen = false;
            return true;
        }
        btnX += ToolbarButton.getIconWidth() + ToolbarButton.GAP;

        // Recently Played
        if (ToolbarButton.isIconClicked(btnX, actionY, btnH, mouseX, mouseY)) {
            recentlyPlayedOpen = !recentlyPlayedOpen;
            searchHistoryOpen = false;
            return true;
        }
        btnX += ToolbarButton.getIconWidth() + ToolbarButton.GAP;

        // Search history dropdown clicks
        int listY = actionY + ACTION_BAR_HEIGHT + SECTION_GAP;
        if (searchHistoryOpen) {
            for (int i = 0; i < searchHistory.size(); i++) {
                if (GuiRender.inside(mouseX, mouseY, searchBarX, listY + i * 18, searchBarW, 18)) {
                    query = searchHistory.get(i);
                    searchHistoryOpen = false;
                    performSearch();
                    return true;
                }
            }
            return false;
        }

        // Recently played dropdown clicks
        if (recentlyPlayedOpen) {
            List<TrackRef> recent = PlayerFacade.getInstance().getPlayHistory();
            for (int i = recent.size() - 1; i >= 0; i--) {
                int idx = recent.size() - 1 - i;
                int rowY = listY + idx * (TrackRow.HEIGHT + ROW_SPACING);
                if (GuiRender.inside(mouseX, mouseY, searchBarX, rowY, searchBarW, TrackRow.HEIGHT)) {
                    TrackRef track = recent.get(i);
                    // Heart click — toggle favorite
                    if (TrackRow.isHeartClicked(searchBarX, rowY, searchBarW, mouseX, mouseY)) {
                        LibraryManager.getInstance().toggleFavorite(track);
                        return true;
                    }
                    // Download click
                    if (TrackRow.isDownloadClicked(searchBarX, rowY, searchBarW, mouseX, mouseY)) {
                        DownloadState dlState = DownloadManager.getInstance().getState(track);
                        if (dlState == DownloadState.NONE || dlState == DownloadState.FAILED) {
                            DownloadManager.getInstance().download(track);
                        }
                        return true;
                    }
                    if (button == 1) {
                        // Right-click: add to queue
                        PlayerFacade.getInstance().addToQueue(track);
                    } else {
                        PlayerFacade.getInstance().playQueue(Collections.singletonList(track), 0);
                    }
                    return true;
                }
            }
            return false;
        }

        // Result clicks
        int resultListY = listY;
        int listH = frame.contentHeight() - (resultListY - y) - INNER_PAD;
        List<TrackRef> displayResults = durationFilterOn ? filterByDuration(searchResults) : searchResults;

        boolean canClickResults;
        if (compact) {
            canClickResults = GuiRender.inside(mouseX, mouseY, x, y, w, h);
        } else {
            canClickResults = GuiRender.inside(mouseX, mouseY, x, resultListY, w, listH);
        }

        if (canClickResults) {
            int currentY = resultListY - (compact ? 0 : (int) scrollOffset);
            for (int i = 0; i < displayResults.size(); i++) {
                if (GuiRender.inside(mouseX, mouseY, searchBarX, currentY, searchBarW, TrackRow.HEIGHT)) {
                    TrackRef track = displayResults.get(i);
                    // Heart click — toggle favorite (any button)
                    if (TrackRow.isHeartClicked(searchBarX, currentY, searchBarW, mouseX, mouseY)) {
                        LibraryManager.getInstance().toggleFavorite(track);
                        return true;
                    }
                    // Download click
                    if (TrackRow.isDownloadClicked(searchBarX, currentY, searchBarW, mouseX, mouseY)) {
                        DownloadState dlState = DownloadManager.getInstance().getState(track);
                        if (dlState == DownloadState.NONE || dlState == DownloadState.FAILED) {
                            DownloadManager.getInstance().download(track);
                        }
                        return true;
                    }
                    if (button == 1) {
                        // Right-click: open playlist context popup
                        contextTrack = track;
                        contextX = (int) mouseX;
                        contextY = (int) mouseY;
                    } else {
                        PlayerFacade.getInstance().playQueue(searchResults, findOriginalIndex(track));
                    }
                    selectedIndex = i;
                    return true;
                }
                currentY += TrackRow.HEIGHT + ROW_SPACING;
            }
        }

        return false;
    }

    public boolean mouseScrolled(GuiFrame frame, double mouseX, double mouseY, double amount) {
        int x = frame.contentX();
        int y = frame.contentY();
        int w = frame.contentWidth();
        int h = frame.contentHeight();

        boolean compact = frame.compact();
        int searchBarY = y + INNER_PAD;
        int listY = searchBarY + SEARCH_BAR_HEIGHT + SECTION_GAP; // search bar only
        if (urlInputOpen) {
            listY += URL_INPUT_HEIGHT + SECTION_GAP;
        }
        listY += ACTION_BAR_HEIGHT + SECTION_GAP; // action bar only
        int listH = frame.contentHeight() - (listY - y) - INNER_PAD;

        boolean canScroll;
        if (compact) {
            canScroll = GuiRender.inside(mouseX, mouseY, x, y, w, h);
        } else {
            canScroll = GuiRender.inside(mouseX, mouseY, x, listY, w, listH);
        }

        if (canScroll) {
            scrollOffset -= amount * 20;
            double maxScroll;
            if (compact) {
                int totalContentH = INNER_PAD + SEARCH_BAR_HEIGHT + SECTION_GAP;
                if (urlInputOpen) {
                    totalContentH += URL_INPUT_HEIGHT + SECTION_GAP;
                }
                totalContentH += ACTION_BAR_HEIGHT + SECTION_GAP;
                if (searchHistoryOpen) {
                    totalContentH += Math.max(1, searchHistory.size()) * 18 + 10;
                } else if (recentlyPlayedOpen) {
                    int maxShow = Math.min(PlayerFacade.getInstance().getPlayHistory().size(), 15);
                    totalContentH += maxShow * (TrackRow.HEIGHT + ROW_SPACING) + 10;
                } else {
                    totalContentH += searchResults.size() * (TrackRow.HEIGHT + ROW_SPACING);
                }
                totalContentH += INNER_PAD;
                maxScroll = Math.max(0, totalContentH - h);
            } else {
                maxScroll = Math.max(0, searchResults.size() * (TrackRow.HEIGHT + ROW_SPACING) - listH);
            }
            if (scrollOffset < 0) scrollOffset = 0;
            if (scrollOffset > maxScroll) scrollOffset = maxScroll;
            return true;
        }
        return false;
    }

    // ── Keyboard ───────────────────────────────────────────────────────────

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;

        // Ctrl+V: paste
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            String clip = getClipboard();
            if (clip != null && !clip.isEmpty()) {
                if (urlInputOpen) { urlInput += clip; urlError = null; }
                else { query += clip; selectedIndex = -1; }
            }
            return true;
        }

        // Ctrl+C: copy current track URL
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            copyCurrentTrackUrl();
            return true;
        }

        // Ctrl+A: clear field
        if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
            if (urlInputOpen) urlInput = "";
            else { query = ""; selectedIndex = -1; }
            return true;
        }

        // URL input mode
        if (urlInputOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { urlInputOpen = false; urlInput = ""; urlError = null; return true; }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !urlInput.isEmpty()) { urlInput = urlInput.substring(0, urlInput.length() - 1); return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { loadUrl(); return true; }
            return false;
        }

        // Query focused mode — typing goes to search bar
        if (queryFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                queryFocused = false;
                return true;
            }
            // Enter always performs search when focused (not play)
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                performSearch();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
                return true;
            }
            // Arrow keys still work for navigation
            List<TrackRef> displayResults = durationFilterOn ? filterByDuration(searchResults) : searchResults;
            if (!displayResults.isEmpty()) {
                if (keyCode == GLFW.GLFW_KEY_DOWN) {
                    selectedIndex = selectedIndex < 0 ? 0 : Math.min(selectedIndex + 1, displayResults.size() - 1);
                    scrollToSelected(displayResults);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_UP) {
                    selectedIndex = selectedIndex < 0 ? 0 : Math.max(selectedIndex - 1, 0);
                    scrollToSelected(displayResults);
                    return true;
                }
            }
            return true; // consume all keys while focused
        }

        // Not focused — arrow keys and Enter on selected result still work
        List<TrackRef> displayResults = durationFilterOn ? filterByDuration(searchResults) : searchResults;
        if (!displayResults.isEmpty()) {
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                selectedIndex = selectedIndex < 0 ? 0 : Math.min(selectedIndex + 1, displayResults.size() - 1);
                scrollToSelected(displayResults);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                selectedIndex = selectedIndex < 0 ? 0 : Math.max(selectedIndex - 1, 0);
                scrollToSelected(displayResults);
                return true;
            }
        }

        // Enter on selected result (when not focused)
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && selectedIndex >= 0 && !displayResults.isEmpty()) {
            TrackRef track = displayResults.get(selectedIndex);
            PlayerFacade.getInstance().playQueue(searchResults, findOriginalIndex(track));
            return true;
        }

        // Close dropdowns
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (searchHistoryOpen) { searchHistoryOpen = false; return true; }
            if (recentlyPlayedOpen) { recentlyPlayedOpen = false; return true; }
            return false;
        }

        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (urlInputOpen) {
            if (codePoint >= ' ') { urlInput += codePoint; urlError = null; return true; }
            return false;
        }
        if (queryFocused) {
            if (codePoint >= ' ') { query += codePoint; selectedIndex = -1; return true; }
            return true; // consume even non-printable when focused
        }
        return false;
    }

    // ── Actions ───────────────────────────────────────────────────────────

    private void loadUrl() {
        String input = urlInput.trim();
        if (input.isEmpty()) return;

        urlLoading = true; urlError = null;
        searchResults.clear(); scrollOffset = 0; selectedIndex = -1;

        String loadUri = resolveYtInput(input);
        ServiceManager.getLavaSearch().search(loadUri)
                .thenAccept(results -> {
                    urlLoading = false;
                    if (!results.isEmpty()) {
                        searchResults = new ArrayList<>(results);
                        PlayerFacade.getInstance().playQueue(searchResults, 0);
                    } else { urlError = "Could not load that URL"; }
                }).exceptionally(error -> { 
                    urlLoading = false; 
                    urlError = formatUserFriendlyError(error); 
                    return null; 
                });
    }

    private void performSearch() {
        if (query.trim().isEmpty()) return;

        isSearching = true; searchError = null;
        searchResults.clear(); scrollOffset = 0; selectedIndex = -1;
        searchHistoryOpen = false; recentlyPlayedOpen = false;

        String searchQuery = query.trim();
        addToSearchHistory(searchQuery);

        if (isYoutube) {
            ServiceManager.getLavaSearch().searchYouTube(searchQuery)
                .thenAccept(results -> {
                    searchResults = new ArrayList<>(results);
                    isSearching = false;
                    if (results.isEmpty()) searchError = "No results found";
                }).exceptionally(error -> { 
                    isSearching = false; 
                    searchError = formatUserFriendlyError(error); 
                    return null; 
                });
        } else {
            ServiceManager.getSpotifySearch().search(searchQuery)
                .thenAccept(results -> {
                    searchResults = new ArrayList<>(results);
                    isSearching = false;
                    if (results.isEmpty()) searchError = "No results from Spotify";
                }).exceptionally(error -> { 
                    isSearching = false; 
                    searchError = formatUserFriendlyError(error); 
                    return null; 
                });
        }
    }

    private String formatUserFriendlyError(Throwable error) {
        if (error == null) return "An unknown error occurred.";
        
        // Unwrap CompletionException if necessary
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        String msg = cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";
        
        if (msg.contains("429") || msg.contains("rate limit") || msg.contains("too many requests")) {
            return "YouTube Rate Limited (Wait a few hours or add po_token in Config)";
        } else if (msg.contains("sign in to confirm your age") || msg.contains("age restricted")) {
            return "This video is age restricted and requires a YouTube account.";
        } else if (msg.contains("video requires login") || msg.contains("please sign in")) {
            return "YouTube blocked this request. (Try adding po_token in Config)";
        } else if (msg.contains("unknownhost") || msg.contains("network is unreachable") || msg.contains("connection reset") || msg.contains("timed out")) {
            return "No Internet Connection (Check your network)";
        } else if (msg.contains("this video is unavailable") || msg.contains("private video") || msg.contains("not available in your country")) {
            return "This video is unavailable or private.";
        } else if (msg.contains("copyright")) {
            return "This video is blocked due to copyright.";
        }
        
        // Fallback for random Lavaplayer errors
        if (cause.getMessage() != null && !cause.getMessage().isEmpty()) {
            return "Error: " + cause.getMessage();
        }
        return "Failed to load track (Unknown error)";
    }

    private void copyCurrentTrackUrl() {
        TrackRef current = PlayerFacade.getInstance().snapshot().getCurrentTrack();
        if (current == null) return;
        String url = current.getExternalUrl();
        if (url == null || url.isEmpty()) {
            String remoteUri = current.getRemoteUri();
            if (remoteUri != null && !remoteUri.isEmpty()) {
                url = remoteUri.length() == 11 ? "https://www.youtube.com/watch?v=" + remoteUri : remoteUri;
            }
        }
        if (url != null && !url.isEmpty()) {
            setClipboard(url);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private boolean isTrackPlaying(TrackRef track) {
        TrackRef current = PlayerFacade.getInstance().snapshot().getCurrentTrack();
        return current != null && current.getId().equals(track.getId());
    }

    private List<TrackRef> filterByDuration(List<TrackRef> tracks) {
        List<TrackRef> filtered = new ArrayList<>();
        for (TrackRef t : tracks) {
            long dur = t.getDurationMs();
            if (dur <= 0 || dur <= DURATION_FILTER_MS) filtered.add(t);
        }
        return filtered;
    }

    private int findOriginalIndex(TrackRef track) {
        for (int i = 0; i < searchResults.size(); i++) {
            if (searchResults.get(i).getId().equals(track.getId())) return i;
        }
        return 0;
    }

    private void scrollToSelected(List<TrackRef> results) {
        if (selectedIndex < 0 || selectedIndex >= results.size()) return;
        double targetScroll = selectedIndex * (TrackRow.HEIGHT + ROW_SPACING) - 60;
        if (targetScroll < 0) targetScroll = 0;
        scrollOffset = targetScroll;
    }

    private void addToSearchHistory(String query) {
        searchHistory.remove(query); // remove duplicate
        searchHistory.add(0, query); // add to front
        while (searchHistory.size() > MAX_SEARCH_HISTORY) searchHistory.remove(searchHistory.size() - 1);

        // Save to config
        com.codexceed.xmusic.config.ConfigManager.get().searchHistory.clear();
        com.codexceed.xmusic.config.ConfigManager.get().searchHistory.addAll(searchHistory);
        com.codexceed.xmusic.config.ConfigManager.save();
    }

    private String resolveYtInput(String input) {
        if (input.startsWith("http://") || input.startsWith("https://")) return input;
        Matcher m = YT_VIDEO_ID.matcher(input);
        if (m.find()) return "https://www.youtube.com/watch?v=" + m.group(1);
        if (input.matches("[a-zA-Z0-9_-]{11}")) return "https://www.youtube.com/watch?v=" + input;
        return "ytsearch:" + input;
    }

    private String getClipboard() {
        try {
            long window = Minecraft.getInstance().getWindow().getWindow();
            String clip = GLFW.glfwGetClipboardString(window);
            return clip != null ? clip : "";
        } catch (Exception e) { return ""; }
    }

    private void setClipboard(String text) {
        try {
            long window = Minecraft.getInstance().getWindow().getWindow();
            GLFW.glfwSetClipboardString(window, text);
        } catch (Exception ignored) {}
    }

    private String formatDuration(long durationMs) {
        if (durationMs <= 0 || durationMs == Long.MAX_VALUE) return "--:--";
        long totalSeconds = durationMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    // ── Context Popup (Add to Playlist / Queue) ──────────────────────────

    private void renderContextPopup(GuiGraphics graphics, Font font, GuiFrame frame) {
        if (contextTrack == null) return;
        int sw = frame.x() + frame.width();
        int sh = frame.y() + frame.height();

        LibraryManager lib = LibraryManager.getInstance();
        Set<String> playlistNames = lib.getPlaylistNames();
        int itemCount = 1 + playlistNames.size(); // "Add to Queue" + each playlist
        int popupW = 120;
        int rowH = 16;
        int popupH = itemCount * rowH + 4;

        // Position popup near right-click location, clamped to screen
        int px = contextX + 4;
        int py = contextY - popupH / 2;
        if (px + popupW > sw - 4) px = contextX - popupW - 4;
        if (py < 4) py = 4;
        if (py + popupH > sh - 4) py = sh - popupH - 4;

        // Background
        graphics.fill(px, py, px + popupW, py + popupH, GuiTheme.TOOLTIP_BG);
        GuiRender.outline(graphics, px, py, popupW, popupH, GuiTheme.TOOLTIP_BORDER);

        int currentY = py + 2;
        // "Add to Queue"
        GuiRender.shadowText(graphics, font, "Add to Queue", px + 6, currentY, GuiTheme.TEXT);
        currentY += rowH;

        // Playlist names
        for (String name : playlistNames) {
            GuiRender.truncated(graphics, font, "+ " + name, px + 6, currentY, popupW - 12, GuiTheme.TEXT_SOFT);
            currentY += rowH;
        }

        // "No playlists" hint
        if (playlistNames.isEmpty()) {
            GuiRender.text(graphics, font, "No playlists", px + 6, currentY, GuiTheme.TEXT_MUTED);
        }
    }

    private boolean clickContextPopup(double mouseX, double mouseY) {
        if (contextTrack == null) return false;

        LibraryManager lib = LibraryManager.getInstance();
        Set<String> playlistNames = lib.getPlaylistNames();
        int itemCount = 1 + playlistNames.size();
        int popupW = 120;
        int rowH = 16;
        int popupH = itemCount * rowH + 4;

        int px = contextX + 4;
        int py = contextY - popupH / 2;

        if (!GuiRender.inside(mouseX, mouseY, px, py, popupW, popupH)) {
            // Clicked outside popup — close it
            contextTrack = null;
            return false;
        }

        // Determine which row was clicked
        int relY = (int) mouseY - py - 2;
        int rowIdx = relY / rowH;

        if (rowIdx == 0) {
            // "Add to Queue"
            PlayerFacade.getInstance().addToQueue(contextTrack);
        } else if (rowIdx - 1 < playlistNames.size()) {
            // Add to specific playlist
            String[] names = playlistNames.toArray(new String[0]);
            if (rowIdx - 1 < names.length) {
                lib.addToPlaylist(names[rowIdx - 1], contextTrack);
            }
        }

        contextTrack = null;
        return true;
    }
    private void renderSourceToggle(GuiGraphics graphics, Font font, int x, int y, int width, boolean compact) {
        int youtubeW = width / 2;

        // MC-style toggle: active side = inset bevel, inactive = raised
        GuiRender.mcButton(graphics, x, y, youtubeW, 18, false, isYoutube);
        GuiRender.mcButton(graphics, x + youtubeW, y, width - youtubeW, 18, false, !isYoutube);

        // Active indicator bar
        if (isYoutube) {
            graphics.fill(x + 3, y + 15, x + youtubeW - 3, y + 17, 0xFFFF0000); // YouTube red
        } else {
            graphics.fill(x + youtubeW + 3, y + 15, x + width - 3, y + 17, GuiTheme.SPOTIFY_GREEN);
        }

        // YouTube side: compass icon + "YouTube" text in red when active
        int ytIconX = compact ? x + (youtubeW - 14) / 2 : x + 4;
        int ytIconY = y + 1;
        int ytColor = isYoutube ? 0xFFFF0000 : GuiTheme.DISABLED;
        IconRenderer.search(graphics, font, ytIconX, ytIconY, 14, 14, ytColor);
        if (!compact) {
            GuiRender.shadowText(graphics, font, "YouTube", ytIconX + 16, y + 5, ytColor);
        }
        // Spotify side: disc icon + "Spotify" text in green when active
        int spIconX = compact ? x + youtubeW + (width - youtubeW - 14) / 2 : x + youtubeW + 4;
        int spIconY = y + 1;
        int spColor = !isYoutube ? GuiTheme.SPOTIFY_GREEN : GuiTheme.DISABLED;
        IconRenderer.album(graphics, font, spIconX, spIconY, 14, 14, spColor);
        if (!compact) {
            GuiRender.shadowText(graphics, font, "Spotify", spIconX + 16, y + 5, spColor);
        }
    }
}
