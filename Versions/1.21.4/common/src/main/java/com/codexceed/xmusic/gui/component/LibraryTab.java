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
import com.codexceed.xmusic.player.PlayerState;
import com.codexceed.xmusic.player.TrackRefMapper;
import com.codexceed.xmusic.service.ServiceManager;
import com.codexceed.xmusic.service.local.LocalMusicService;
import com.codexceed.xmusic.source.TrackRef;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Desktop;
import java.io.File;

import java.util.*;

/**
 * Combined Library + Groups tab.
 *
 * Two-level drill-down navigation:
 *   CATEGORIES → (FAVORITES | PLAYLIST_LIST | GROUP_ARTIST | GROUP_ALBUM | GROUP_SOURCE)
 *   → drill into a specific playlist or group → track list view
 */
public final class LibraryTab {

    // ── View State ──────────────────────────────────────────────────────

    private enum View { CATEGORIES, FAVORITES, MOST_REPLAYED, HISTORY, PLAYLIST_LIST, PLAYLIST_DETAIL, GROUP_ARTIST, GROUP_ALBUM, GROUP_SOURCE, GROUP_DETAIL, LOCAL }

    private View currentView = View.CATEGORIES;
    private String selectedPlaylist = null;     // for PLAYLIST_DETAIL
    private String selectedGroup = null;        // for GROUP_DETAIL
    private View selectedGroupType = null;      // GROUP_ARTIST / GROUP_ALBUM / GROUP_SOURCE

    // ── Playlist Create State ────────────────────────────────────────────

    private boolean creatingPlaylist = false;
    private String newPlaylistName = "";

    // ── Local View State ─────────────────────────────────────────────────

    private boolean localSearchExpanded = false;
    private boolean localSearchFocused = false;
    private String localSearchQuery = "";

    // ── Scroll ──────────────────────────────────────────────────────────

    private double scrollOffset = 0;
    private double maxScroll = 0;

    // ── Constants ───────────────────────────────────────────────────────

    private static final int INNER_PAD = 6;
    private static final int SECTION_GAP = 6;
    private static final int ROW_H = 22;
    private static final int ICON_SIZE = 14;
    private static final int ICON_PAD = 4;
    private static final int LABEL_GAP = 6;
    private static final int CHEVRON_SIZE = 10;
    private static final int ACTION_BAR_H = 20;
    private static final int CREATE_INPUT_H = 18;

    private final TrackRow trackRow = new TrackRow();

    // ── Public Navigation ────────────────────────────────────────────────

    /** Navigate to a specific view from external tabs (e.g. Home "See all"). */
    public void openView(String viewName) {
        try {
            currentView = View.valueOf(viewName);
            scrollOffset = 0;
        } catch (IllegalArgumentException ignored) {
            currentView = View.CATEGORIES;
        }
    }

    // ── Render ──────────────────────────────────────────────────────────

    public void render(GuiGraphics graphics, Font font, GuiFrame frame, int mouseX, int mouseY) {
        int x = frame.contentX();
        int y = frame.contentY();
        int w = frame.contentWidth();
        int h = frame.contentHeight();
        int screenW = frame.x() + frame.width();
        int screenH = frame.y() + frame.height();

        switch (currentView) {
            case CATEGORIES: renderCategories(graphics, font, x, y, w, h, mouseX, mouseY, screenW, screenH); break;
            case FAVORITES: renderTrackList(graphics, font, x, y, w, h, "Favorites", LibraryManager.getInstance().getFavorites(), mouseX, mouseY, screenW, screenH); break;
            case MOST_REPLAYED: renderTrackList(graphics, font, x, y, w, h, "Most Replayed", LibraryManager.getInstance().getMostReplayed(), mouseX, mouseY, screenW, screenH); break;
            case HISTORY: renderTrackList(graphics, font, x, y, w, h, "History", LibraryManager.getInstance().getTodayHistory(), mouseX, mouseY, screenW, screenH); break;
            case PLAYLIST_LIST: renderPlaylistList(graphics, font, x, y, w, h, mouseX, mouseY, screenW, screenH); break;
            case PLAYLIST_DETAIL: renderPlaylistDetail(graphics, font, x, y, w, h, mouseX, mouseY, screenW, screenH); break;
            case GROUP_ARTIST: renderGroupList(graphics, font, x, y, w, h, "By Artist", LibraryManager.getInstance().getAutoGroupByArtist(), mouseX, mouseY, screenW, screenH); break;
            case GROUP_ALBUM: renderGroupList(graphics, font, x, y, w, h, "By Album", LibraryManager.getInstance().getAutoGroupByAlbum(), mouseX, mouseY, screenW, screenH); break;
            case GROUP_SOURCE: renderGroupList(graphics, font, x, y, w, h, "By Source", LibraryManager.getInstance().getAutoGroupBySource(), mouseX, mouseY, screenW, screenH); break;
            case GROUP_DETAIL: renderGroupDetail(graphics, font, x, y, w, h, mouseX, mouseY, screenW, screenH); break;
            case LOCAL: renderLocalView(graphics, font, x, y, w, h, mouseX, mouseY, screenW, screenH); break;
        }
    }

    // ── Category View ───────────────────────────────────────────────────

    private void renderCategories(GuiGraphics g, Font f, int x, int y, int w, int h, int mx, int my, int sw, int sh) {
        LibraryManager lib = LibraryManager.getInstance();
        int rowX = x + INNER_PAD;
        int rowW = w - INNER_PAD * 2;
        int currentY = y + INNER_PAD;

        // Title
        GuiRender.shadowText(g, f, "Library", rowX, currentY, GuiTheme.ACCENT);
        currentY += 14;

        // Category rows
        LocalMusicService localService = ServiceManager.getLocalMusic();
        int localCount = localService != null ? localService.getTrackCount() : 0;
        CategoryDef[] categories = {
            new CategoryDef("Favorites", lib.getFavorites().size(), IconRenderer::heartFilled, View.FAVORITES),
            new CategoryDef("Local Files", localCount, IconRenderer::musicNote, View.LOCAL),
            new CategoryDef("Playlists", lib.getPlaylistNames().size(), IconRenderer::playlistBook, View.PLAYLIST_LIST),
            new CategoryDef("By Artist", lib.getAutoGroupByArtist().size(), IconRenderer::musicNote, View.GROUP_ARTIST),
            new CategoryDef("By Album", lib.getAutoGroupByAlbum().size(), IconRenderer::album, View.GROUP_ALBUM),
            new CategoryDef("By Source", lib.getAutoGroupBySource().size(), IconRenderer::source, View.GROUP_SOURCE),
        };

        for (int i = 0; i < categories.length; i++) {
            CategoryDef cat = categories[i];
            if (currentY + ROW_H > y + h) break;

            boolean hovered = GuiRender.inside(mx, my, rowX, currentY, rowW, ROW_H);

            // Row background — no hover glow on groups, just subtle bg shift
            int bgColor = hovered ? GuiTheme.PANEL_HOVER : GuiTheme.PANEL;
            g.fill(rowX, currentY, rowX + rowW, currentY + ROW_H, bgColor);
            GuiRender.bevelHover(g, rowX, currentY, rowW, ROW_H, false, hovered);

            // Icon
            int iconX = rowX + ICON_PAD;
            int iconY = currentY + (ROW_H - ICON_SIZE) / 2;
            int iconColor = hovered ? GuiTheme.ACCENT : GuiTheme.TEXT;
            cat.icon.render(g, f, iconX, iconY, ICON_SIZE, ICON_SIZE, iconColor);

            // Label
            int labelX = iconX + ICON_SIZE + LABEL_GAP;
            int labelY = currentY + (ROW_H - 8) / 2;
            int labelColor = hovered ? GuiTheme.ACCENT : GuiTheme.TEXT;
            GuiRender.shadowText(g, f, cat.label, labelX, labelY, labelColor);

            // Count badge
            String countStr = String.valueOf(cat.count);
            int countW = f.width(countStr) + 6;
            int countX = rowX + rowW - CHEVRON_SIZE - LABEL_GAP - countW - 4;
            int countY = currentY + (ROW_H - 10) / 2;
            g.fill(countX, countY, countX + countW, countY + 10, GuiTheme.PANEL_DARK);
            GuiRender.bevel(g, countX, countY, countW, 10, true);
            GuiRender.centeredText(g, f, countStr, countX + countW / 2, countY + 1, GuiTheme.TEXT_MUTED);

            // Chevron
            int chevX = rowX + rowW - CHEVRON_SIZE - ICON_PAD;
            int chevY = currentY + (ROW_H - CHEVRON_SIZE) / 2;
            IconRenderer.chevronRight(g, f, chevX, chevY, CHEVRON_SIZE, CHEVRON_SIZE, hovered ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);

            // Tooltip
            if (hovered) {
                GuiRender.tooltip(g, f, cat.label, mx, my, sw, sh);
            }

            currentY += ROW_H + 2;
        }
    }

    // ── Playlist List View ───────────────────────────────────────────────

    private void renderPlaylistList(GuiGraphics g, Font f, int x, int y, int w, int h, int mx, int my, int sw, int sh) {
        LibraryManager lib = LibraryManager.getInstance();
        int rowX = x + INNER_PAD;
        int rowW = w - INNER_PAD * 2;
        int currentY = y + INNER_PAD;

        // Back button + title
        currentY = renderBackHeader(g, f, rowX, currentY, rowW, "Playlists", mx, my, sw, sh);

        // Create playlist button
        int createBtnW = 60;
        boolean createHover = GuiRender.inside(mx, my, rowX, currentY, createBtnW, ACTION_BAR_H);
        float createLerp = HoverTracker.tick("lib_create", createHover);
        GuiRender.mcButton(g, rowX, currentY, createBtnW, ACTION_BAR_H, createHover, false);
        IconRenderer.plus(g, f, rowX + 3, currentY + 3, 12, 14, createHover ? GuiTheme.ACCENT : GuiTheme.TEXT);
        GuiRender.text(g, f, "New", rowX + 18, currentY + 6, createHover ? GuiTheme.ACCENT : GuiTheme.TEXT);
        if (createHover) GuiRender.tooltip(g, f, "Create Playlist", mx, my, sw, sh);

        // Inline name input when creating
        if (creatingPlaylist) {
            int inputX = rowX + createBtnW + 4;
            int inputW = rowW - createBtnW - 4;
            GuiRender.mcWell(g, inputX, currentY, inputW, CREATE_INPUT_H);
            String display = newPlaylistName + "_";
            GuiRender.truncated(g, f, display, inputX + 4, currentY + 5, inputW - 8, GuiTheme.TEXT);
        }
        currentY += ACTION_BAR_H + SECTION_GAP;

        // List area starts here
        int listTop = currentY;
        int listH = y + h - listTop - INNER_PAD;
        if (listH < 0) listH = 0;

        // Compute max scroll
        Set<String> names = lib.getPlaylistNames();
        int totalContentH3 = names.size() * (ROW_H + 2);
        maxScroll = Math.max(0, totalContentH3 - listH);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        // Scissored playlist rows
        g.enableScissor(rowX, listTop, rowX + rowW, listTop + listH);
        int drawY = listTop - (int) scrollOffset;

        int idx = 0;
        for (String name : names) {
            if (drawY + ROW_H < listTop) { drawY += ROW_H + 2; idx++; continue; }
            if (drawY > listTop + listH) break;

            List<TrackRef> tracks = lib.getPlaylist(name);
            boolean hovered = GuiRender.inside(mx, my, rowX, drawY, rowW - 20, ROW_H);

            // Row bg — no hover glow on groups
            int bgColor = hovered ? GuiTheme.PANEL_HOVER : GuiTheme.PANEL;
            g.fill(rowX, drawY, rowX + rowW - 20, drawY + ROW_H, bgColor);
            GuiRender.bevelHover(g, rowX, drawY, rowW - 20, ROW_H, false, hovered);

            // Icon
            int iconX = rowX + ICON_PAD;
            int iconY = drawY + (ROW_H - ICON_SIZE) / 2;
            IconRenderer.playlistBook(g, f, iconX, iconY, ICON_SIZE, ICON_SIZE, hovered ? GuiTheme.ACCENT : GuiTheme.TEXT);

            // Name
            int labelX = iconX + ICON_SIZE + LABEL_GAP;
            int labelY = drawY + (ROW_H - 8) / 2;
            GuiRender.truncated(g, f, name, labelX, labelY, rowW - 80, hovered ? GuiTheme.ACCENT : GuiTheme.TEXT);

            // Count
            String cnt = tracks.size() + " tracks";
            int cntW = f.width(cnt) + 6;
            int cntX = rowX + rowW - 20 - CHEVRON_SIZE - LABEL_GAP - cntW - 4;
            GuiRender.text(g, f, cnt, cntX, drawY + (ROW_H - 8) / 2, GuiTheme.TEXT_MUTED);

            // Chevron
            int chevX = rowX + rowW - 20 - CHEVRON_SIZE - ICON_PAD;
            int chevY = drawY + (ROW_H - CHEVRON_SIZE) / 2;
            IconRenderer.chevronRight(g, f, chevX, chevY, CHEVRON_SIZE, CHEVRON_SIZE, hovered ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);

            if (hovered) GuiRender.tooltip(g, f, name, mx, my, sw, sh);

            // Delete button (right side)
            int delX = rowX + rowW - 18;
            int delSize = 14;
            boolean delHover = GuiRender.inside(mx, my, delX, drawY + (ROW_H - delSize) / 2, delSize, delSize);
            HoverTracker.tick("lib_pldel_" + idx, delHover);
            IconRenderer.delete(g, f, delX, drawY + (ROW_H - delSize) / 2, delSize, delSize, delHover ? 0xFFFF4444 : GuiTheme.TEXT_MUTED);
            if (delHover) GuiRender.tooltip(g, f, "Delete", mx, my, sw, sh);

            drawY += ROW_H + 2;
            idx++;
        }
        g.disableScissor();

        // Empty state
        if (names.isEmpty() && !creatingPlaylist) {
            GuiRender.text(g, f, "No playlists yet. Click + New to create one.", rowX, listTop, GuiTheme.TEXT_MUTED);
        }
    }

    // ── Playlist Detail View ─────────────────────────────────────────────

    private void renderPlaylistDetail(GuiGraphics g, Font f, int x, int y, int w, int h, int mx, int my, int sw, int sh) {
        LibraryManager lib = LibraryManager.getInstance();
        List<TrackRef> tracks = selectedPlaylist != null ? lib.getPlaylist(selectedPlaylist) : Collections.emptyList();
        String title = selectedPlaylist != null ? selectedPlaylist : "Playlist";
        renderTrackList(g, f, x, y, w, h, title, tracks, mx, my, sw, sh);
    }

    // ── Group List View (artist/album/source) ────────────────────────────

    private void renderGroupList(GuiGraphics g, Font f, int x, int y, int w, int h, String title, Map<String, List<TrackRef>> groups, int mx, int my, int sw, int sh) {
        int rowX = x + INNER_PAD;
        int rowW = w - INNER_PAD * 2;
        int currentY = y + INNER_PAD;

        // Back + title
        currentY = renderBackHeader(g, f, rowX, currentY, rowW, title, mx, my, sw, sh);

        // List area starts here
        int listTop = currentY;
        int listH = y + h - listTop - INNER_PAD;
        if (listH < 0) listH = 0;

        // Compute max scroll
        int totalContentH2 = groups.size() * (ROW_H + 2);
        maxScroll = Math.max(0, totalContentH2 - listH);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        // Scissored track rows
        g.enableScissor(rowX, listTop, rowX + rowW, listTop + listH);
        int drawY = listTop - (int) scrollOffset;

        int idx = 0;
        for (Map.Entry<String, List<TrackRef>> entry : groups.entrySet()) {
            if (drawY + ROW_H < listTop) { drawY += ROW_H + 2; idx++; continue; }
            if (drawY > listTop + listH) break;

            String groupName = entry.getKey();
            int count = entry.getValue().size();
            boolean hovered = GuiRender.inside(mx, my, rowX, drawY, rowW, ROW_H);

            // Row bg — no hover glow on groups
            int bgColor = hovered ? GuiTheme.PANEL_HOVER : GuiTheme.PANEL;
            g.fill(rowX, drawY, rowX + rowW, drawY + ROW_H, bgColor);
            GuiRender.bevelHover(g, rowX, drawY, rowW, ROW_H, false, hovered);

            // Icon based on type
            int iconX = rowX + ICON_PAD;
            int iconY = drawY + (ROW_H - ICON_SIZE) / 2;
            int iconColor = hovered ? GuiTheme.ACCENT : GuiTheme.TEXT;
            if (title.contains("Artist")) IconRenderer.musicNote(g, f, iconX, iconY, ICON_SIZE, ICON_SIZE, iconColor);
            else if (title.contains("Album")) IconRenderer.album(g, f, iconX, iconY, ICON_SIZE, ICON_SIZE, iconColor);
            else IconRenderer.source(g, f, iconX, iconY, ICON_SIZE, ICON_SIZE, iconColor);

            // Label
            int labelX = iconX + ICON_SIZE + LABEL_GAP;
            GuiRender.truncated(g, f, groupName, labelX, drawY + (ROW_H - 8) / 2, rowW - 80, hovered ? GuiTheme.ACCENT : GuiTheme.TEXT);

            // Count
            String cnt = count + " tracks";
            int cntW = f.width(cnt) + 6;
            int cntX = rowX + rowW - CHEVRON_SIZE - LABEL_GAP - cntW - 4;
            GuiRender.text(g, f, cnt, cntX, drawY + (ROW_H - 8) / 2, GuiTheme.TEXT_MUTED);

            // Chevron
            int chevX = rowX + rowW - CHEVRON_SIZE - ICON_PAD;
            int chevY = drawY + (ROW_H - CHEVRON_SIZE) / 2;
            IconRenderer.chevronRight(g, f, chevX, chevY, CHEVRON_SIZE, CHEVRON_SIZE, hovered ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);

            if (hovered) GuiRender.tooltip(g, f, groupName, mx, my, sw, sh);

            drawY += ROW_H + 2;
            idx++;
        }
        g.disableScissor();

        if (groups.isEmpty()) {
            GuiRender.text(g, f, "No tracks to group. Add some favorites first.", rowX, listTop, GuiTheme.TEXT_MUTED);
        }
    }

    // ── Group Detail View ────────────────────────────────────────────────

    private void renderGroupDetail(GuiGraphics g, Font f, int x, int y, int w, int h, int mx, int my, int sw, int sh) {
        Map<String, List<TrackRef>> groups = resolveGroupMap();
        List<TrackRef> tracks = selectedGroup != null ? groups.getOrDefault(selectedGroup, Collections.emptyList()) : Collections.emptyList();
        String title = selectedGroup != null ? selectedGroup : "Group";
        renderTrackList(g, f, x, y, w, h, title, tracks, mx, my, sw, sh);
    }

    // ── Shared: Track List View ──────────────────────────────────────────

    private void renderTrackList(GuiGraphics g, Font f, int x, int y, int w, int h, String title, Collection<TrackRef> trackCollection, int mx, int my, int sw, int sh) {
        List<TrackRef> tracks = trackCollection instanceof List ? (List<TrackRef>) trackCollection : new ArrayList<>(trackCollection);
        int rowX = x + INNER_PAD;
        int rowW = w - INNER_PAD * 2;
        int currentY = y + INNER_PAD;

        // Back + title
        currentY = renderBackHeader(g, f, rowX, currentY, rowW, title, mx, my, sw, sh);

        // Action bar
        PlayerState state = PlayerFacade.getInstance().snapshot();
        TrackRef currentTrack = state.getCurrentTrack();

        int btnX = rowX;
        int btnH = ACTION_BAR_H - 4;

        // Play All
        boolean playAllHover = GuiRender.inside(mx, my, btnX, currentY, ToolbarButton.getWidth(f, "Play All"), btnH) && !tracks.isEmpty();
        btnX += ToolbarButton.render(g, f, btnX, currentY, btnH, "Play All", IconRenderer::playAll, playAllHover, false) + ToolbarButton.GAP;

        // Shuffle
        boolean shuffleHover = GuiRender.inside(mx, my, btnX, currentY, ToolbarButton.getWidth(f, "Shuffle"), btnH) && !tracks.isEmpty();
        btnX += ToolbarButton.render(g, f, btnX, currentY, btnH, "Shuffle", IconRenderer::shuffle, shuffleHover, false) + ToolbarButton.GAP;

        // Track count (right side)
        String countLabel = tracks.size() + " tracks";
        GuiRender.text(g, f, countLabel, rowX + rowW - f.width(countLabel) - 4, currentY + 3, GuiTheme.TEXT_MUTED);

        currentY += ACTION_BAR_H + SECTION_GAP;

        // Solid background behind header so scrolling tracks don't show through
        int headerBottom = currentY;
        g.fill(x, y + INNER_PAD, x + w, headerBottom, GuiTheme.PANEL);

        // Re-render header on top of solid background
        int hrY = y + INNER_PAD;
        hrY = renderBackHeader(g, f, rowX, hrY, rowW, title, mx, my, sw, sh);
        int hbtnX = rowX;
        boolean hpa = GuiRender.inside(mx, my, hbtnX, hrY, ToolbarButton.getWidth(f, "Play All"), btnH) && !tracks.isEmpty();
        hbtnX += ToolbarButton.render(g, f, hbtnX, hrY, btnH, "Play All", IconRenderer::playAll, hpa, false) + ToolbarButton.GAP;
        boolean hs = GuiRender.inside(mx, my, hbtnX, hrY, ToolbarButton.getWidth(f, "Shuffle"), btnH) && !tracks.isEmpty();
        hbtnX += ToolbarButton.render(g, f, hbtnX, hrY, btnH, "Shuffle", IconRenderer::shuffle, hs, false) + ToolbarButton.GAP;
        GuiRender.text(g, f, countLabel, rowX + rowW - f.width(countLabel) - 4, hrY + 3, GuiTheme.TEXT_MUTED);

        // Track rows (scissored)
        int listH = y + h - currentY - INNER_PAD;
        if (listH < 0) listH = 0;
        g.enableScissor(rowX, currentY, rowX + rowW, currentY + listH);
        int drawY = currentY - (int) scrollOffset;

        for (int i = 0; i < tracks.size(); i++) {
            TrackRef track = tracks.get(i);
            if (drawY + TrackRow.HEIGHT < currentY) { drawY += TrackRow.HEIGHT + 2; continue; }
            if (drawY > currentY + listH) break;

            boolean isPlaying = currentTrack != null && currentTrack.getId().equals(track.getId()) && currentTrack.getSourceId().equals(track.getSourceId());
            boolean isFav = LibraryManager.getInstance().isFavorite(track);
            String durationStr = track.getDurationMs() > 0 ? formatDuration(track.getDurationMs()) : "--:--";

            trackRow.render(g, f, rowX, drawY, rowW,
                    track.getTitle(), track.getArtist(), durationStr,
                    isPlaying, false, isFav, track, mx, my, sw, sh);
            drawY += TrackRow.HEIGHT + 2;
        }
        g.disableScissor();

        // Empty state
        if (tracks.isEmpty()) {
            GuiRender.text(g, f, "No tracks here yet.", rowX, currentY, GuiTheme.TEXT_MUTED);
        }

        // Compute max scroll for this view
        int totalContentH = tracks.size() * (TrackRow.HEIGHT + 2);
        maxScroll = Math.max(0, totalContentH - listH);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    }

    // ── Local Files View ────────────────────────────────────────────────

    private void renderLocalView(GuiGraphics g, Font f, int x, int y, int w, int h, int mx, int my, int sw, int sh) {
        LocalMusicService localService = ServiceManager.getLocalMusic();
        int rowX = x + INNER_PAD;
        int rowW = w - INNER_PAD * 2;
        int currentY = y + INNER_PAD;

        // Back + title
        currentY = renderBackHeader(g, f, rowX, currentY, rowW, "Local Files", mx, my, sw, sh);

        // Action bar: Play All + Shuffle + Rescan + Search + Folder (auto-sized)
        PlayerState state = PlayerFacade.getInstance().snapshot();
        TrackRef currentTrack = state.getCurrentTrack();
        List<TrackRef> localTracks = getLocalTrackRefs();
        List<TrackRef> filteredTracks = filterLocalTracks(localTracks);

        int btnX = rowX;
        int btnH = ACTION_BAR_H - 4;

        // Play All
        boolean playAllHover = GuiRender.inside(mx, my, btnX, currentY, ToolbarButton.getWidth(f, "Play All"), btnH) && !filteredTracks.isEmpty();
        btnX += ToolbarButton.render(g, f, btnX, currentY, btnH, "Play All", IconRenderer::playAll, playAllHover, false) + ToolbarButton.GAP;

        // Shuffle
        boolean shuffleHover = GuiRender.inside(mx, my, btnX, currentY, ToolbarButton.getWidth(f, "Shuffle"), btnH) && !filteredTracks.isEmpty();
        btnX += ToolbarButton.render(g, f, btnX, currentY, btnH, "Shuffle", IconRenderer::shuffle, shuffleHover, false) + ToolbarButton.GAP;

        // Rescan
        boolean rescanHover = GuiRender.inside(mx, my, btnX, currentY, ToolbarButton.getWidth(f, "Rescan"), btnH);
        btnX += ToolbarButton.render(g, f, btnX, currentY, btnH, "Rescan", IconRenderer::rescan, rescanHover, false) + ToolbarButton.GAP;

        // Search toggle (icon-only)
        boolean searchHover = GuiRender.inside(mx, my, btnX, currentY, ToolbarButton.getIconWidth(), btnH);
        btnX += ToolbarButton.renderIconOnly(g, f, btnX, currentY, btnH, IconRenderer::search, searchHover, localSearchExpanded) + ToolbarButton.GAP;

        // Folder button (icon-only, right side)
        int folderBtnX = rowX + rowW - ToolbarButton.getIconWidth() - 4;
        boolean folderHover = GuiRender.inside(mx, my, folderBtnX, currentY, ToolbarButton.getIconWidth(), btnH);
        ToolbarButton.renderIconOnly(g, f, folderBtnX, currentY, btnH, IconRenderer::folder, folderHover, false);
        if (folderHover) GuiRender.tooltip(g, f, "Open Folder", mx, my, sw, sh);

        // Track count (right of buttons, before folder)
        String countLabel = localTracks.size() + " files";
        if (!localSearchQuery.isEmpty()) {
            countLabel = filteredTracks.size() + "/" + localTracks.size();
        }
        int countX = folderBtnX - f.width(countLabel) - 8;
        if (countX > btnX) {
            GuiRender.text(g, f, countLabel, countX, currentY + 3, GuiTheme.TEXT_MUTED);
        }

        currentY += ACTION_BAR_H + 2;

        // Search bar (if expanded)
        if (localSearchExpanded) {
            GuiRender.mcWell(g, rowX, currentY, rowW, 16);
            // Focus highlight border
            if (localSearchFocused) {
                g.fill(rowX - 1, currentY - 1, rowX + rowW + 1, currentY, GuiTheme.GLOW_ACCENT);
                g.fill(rowX - 1, currentY + 16, rowX + rowW + 1, currentY + 17, GuiTheme.GLOW_ACCENT);
                g.fill(rowX - 1, currentY, rowX, currentY + 16, GuiTheme.GLOW_ACCENT);
                g.fill(rowX + rowW, currentY, rowX + rowW + 1, currentY + 16, GuiTheme.GLOW_ACCENT);
            }
            String display = localSearchQuery.isEmpty() ? "Search local files..." : localSearchQuery;
            int color = localSearchQuery.isEmpty() ? GuiTheme.TEXT_MUTED : GuiTheme.TEXT;
            GuiRender.truncated(g, f, display, rowX + 4, currentY + 4, rowW - 24, color);
            // Blinking cursor when focused
            if (localSearchFocused) {
                boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
                if (blink) {
                    int cursorX = rowX + 4 + (localSearchQuery.isEmpty() ? 0 : f.width(localSearchQuery));
                    g.fill(cursorX, currentY + 3, cursorX + 1, currentY + 13, GuiTheme.ACCENT);
                }
            }
            if (!localSearchQuery.isEmpty()) {
                // Clear button
                int clearX = rowX + rowW - 14;
                int clearY = currentY + 3;
                boolean clearHover = GuiRender.inside(mx, my, clearX, clearY, 10, 10);
                IconRenderer.clear(g, f, clearX, clearY, 10, 10, clearHover ? GuiTheme.DANGER : GuiTheme.TEXT_MUTED);
            }
            currentY += 18;
        }

        // Scanning indicator
        if (localService != null && localService.isScanning()) {
            float pulse = (float)(Math.sin(System.currentTimeMillis() / 300.0) * 0.3 + 0.7);
            int pulseColor = ((int)(0xFF * pulse) << 24) | (GuiTheme.ACCENT & 0x00FFFFFF);
            GuiRender.shadowText(g, f, "Scanning...", rowX + 4, currentY, pulseColor);
            currentY += 12;
        }

        // Solid background behind header so scrolling tracks don't show through
        int headerBottom = currentY;
        g.fill(x, y + INNER_PAD, x + w, headerBottom, GuiTheme.PANEL);

        // Re-render header on top of solid background
        int hrY = y + INNER_PAD;
        hrY = renderBackHeader(g, f, rowX, hrY, rowW, "Local Files", mx, my, sw, sh);
        int hbtnX = rowX;
        boolean hpa = GuiRender.inside(mx, my, hbtnX, hrY, ToolbarButton.getWidth(f, "Play All"), btnH) && !filteredTracks.isEmpty();
        hbtnX += ToolbarButton.render(g, f, hbtnX, hrY, btnH, "Play All", IconRenderer::playAll, hpa, false) + ToolbarButton.GAP;
        boolean hs = GuiRender.inside(mx, my, hbtnX, hrY, ToolbarButton.getWidth(f, "Shuffle"), btnH) && !filteredTracks.isEmpty();
        hbtnX += ToolbarButton.render(g, f, hbtnX, hrY, btnH, "Shuffle", IconRenderer::shuffle, hs, false) + ToolbarButton.GAP;
        boolean hr = GuiRender.inside(mx, my, hbtnX, hrY, ToolbarButton.getWidth(f, "Rescan"), btnH);
        hbtnX += ToolbarButton.render(g, f, hbtnX, hrY, btnH, "Rescan", IconRenderer::rescan, hr, false) + ToolbarButton.GAP;
        boolean hsh = GuiRender.inside(mx, my, hbtnX, hrY, ToolbarButton.getIconWidth(), btnH);
        hbtnX += ToolbarButton.renderIconOnly(g, f, hbtnX, hrY, btnH, IconRenderer::search, hsh, localSearchExpanded) + ToolbarButton.GAP;
        int hfbx = rowX + rowW - ToolbarButton.getIconWidth() - 4;
        boolean hfh = GuiRender.inside(mx, my, hfbx, hrY, ToolbarButton.getIconWidth(), btnH);
        ToolbarButton.renderIconOnly(g, f, hfbx, hrY, btnH, IconRenderer::folder, hfh, false);
        GuiRender.text(g, f, countLabel, folderBtnX - f.width(countLabel) - 8, hrY + 3, GuiTheme.TEXT_MUTED);
        hrY += ACTION_BAR_H + 2;
        if (localSearchExpanded) {
            GuiRender.mcWell(g, rowX, hrY, rowW, 16);
            if (localSearchFocused) {
                g.fill(rowX - 1, hrY - 1, rowX + rowW + 1, hrY, GuiTheme.GLOW_ACCENT);
                g.fill(rowX - 1, hrY + 16, rowX + rowW + 1, hrY + 17, GuiTheme.GLOW_ACCENT);
                g.fill(rowX - 1, hrY, rowX, hrY + 16, GuiTheme.GLOW_ACCENT);
                g.fill(rowX + rowW, hrY, rowX + rowW + 1, hrY + 16, GuiTheme.GLOW_ACCENT);
            }
            String hdisplay = localSearchQuery.isEmpty() ? "Search local files..." : localSearchQuery;
            int hcolor = localSearchQuery.isEmpty() ? GuiTheme.TEXT_MUTED : GuiTheme.TEXT;
            GuiRender.truncated(g, f, hdisplay, rowX + 4, hrY + 4, rowW - 24, hcolor);
            if (localSearchFocused) {
                boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
                if (blink) g.fill(rowX + 4 + (localSearchQuery.isEmpty() ? 0 : f.width(localSearchQuery)), hrY + 3, rowX + 5 + (localSearchQuery.isEmpty() ? 0 : f.width(localSearchQuery)), hrY + 13, GuiTheme.ACCENT);
            }
            if (!localSearchQuery.isEmpty()) {
                IconRenderer.clear(g, f, rowX + rowW - 14, hrY + 3, 10, 10, GuiTheme.TEXT_MUTED);
            }
            hrY += 18;
        }
        if (localService != null && localService.isScanning()) {
            float pulse2 = (float)(Math.sin(System.currentTimeMillis() / 300.0) * 0.3 + 0.7);
            GuiRender.shadowText(g, f, "Scanning...", rowX + 4, hrY, ((int)(0xFF * pulse2) << 24) | (GuiTheme.ACCENT & 0x00FFFFFF));
        }

        // Track rows (scissored)
        int listH = y + h - currentY - INNER_PAD;
        if (listH < 0) listH = 0;
        g.enableScissor(rowX, currentY, rowX + rowW, currentY + listH);
        int drawY = currentY - (int) scrollOffset;

        for (int i = 0; i < filteredTracks.size(); i++) {
            TrackRef track = filteredTracks.get(i);
            if (drawY + TrackRow.HEIGHT < currentY) { drawY += TrackRow.HEIGHT + 2; continue; }
            if (drawY > currentY + listH) break;

            boolean isPlaying = currentTrack != null && currentTrack.getId().equals(track.getId()) && currentTrack.getSourceId().equals(track.getSourceId());
            boolean isFav = LibraryManager.getInstance().isFavorite(track);
            String durationStr = track.getDurationMs() > 0 ? formatDuration(track.getDurationMs()) : "--:--";

            trackRow.render(g, f, rowX, drawY, rowW,
                    track.getTitle(), track.getArtist(), durationStr,
                    isPlaying, false, isFav, track, mx, my, sw, sh);
            drawY += TrackRow.HEIGHT + 2;
        }
        g.disableScissor();

        // Empty state
        if (filteredTracks.isEmpty()) {
            if (localSearchExpanded && !localSearchQuery.isEmpty()) {
                GuiRender.text(g, f, "No results for \"" + localSearchQuery + "\"", rowX, currentY, GuiTheme.TEXT_MUTED);
            } else if (localTracks.isEmpty()) {
                GuiRender.text(g, f, "No local files found.", rowX, currentY, GuiTheme.TEXT_MUTED);
                GuiRender.text(g, f, "Drop MP3/OGG files in the xmusic/local/ folder.", rowX, currentY + 12, GuiTheme.TEXT_MUTED);
            }
        }

        // Compute max scroll for this view
        int totalContentH = filteredTracks.size() * (TrackRow.HEIGHT + 2);
        maxScroll = Math.max(0, totalContentH - listH);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    }

    /** Get local tracks as TrackRef list */
    private List<TrackRef> getLocalTrackRefs() {
        LocalMusicService localService = ServiceManager.getLocalMusic();
        if (localService == null) return Collections.emptyList();
        List<TrackRef> refs = new ArrayList<>();
        for (com.codexceed.xmusic.audio.AudioTrack at : localService.getTracks()) {
            TrackRef ref = TrackRefMapper.fromAudioTrack(at);
            if (ref != null) refs.add(ref);
        }
        return refs;
    }

    /** Filter local tracks by search query */
    private List<TrackRef> filterLocalTracks(List<TrackRef> tracks) {
        if (localSearchQuery.isEmpty()) return tracks;
        String q = localSearchQuery.toLowerCase();
        List<TrackRef> filtered = new ArrayList<>();
        for (TrackRef t : tracks) {
            String title = t.getTitle() != null ? t.getTitle().toLowerCase() : "";
            String artist = t.getArtist() != null ? t.getArtist().toLowerCase() : "";
            String album = t.getAlbum() != null ? t.getAlbum().toLowerCase() : "";
            if (title.contains(q) || artist.contains(q) || album.contains(q)) {
                filtered.add(t);
            }
        }
        return filtered;
    }

    // ── Shared: Back Header ──────────────────────────────────────────────

    private int renderBackHeader(GuiGraphics g, Font f, int x, int y, int w, String title, int mx, int my, int sw, int sh) {
        // Back button
        int backSize = 16;
        boolean backHover = GuiRender.inside(mx, my, x, y, backSize + 4, backSize);
        float backLerp = HoverTracker.tick("lib_back", backHover);
        if (backHover) {
            g.fill(x, y, x + backSize + 4, y + backSize, GuiTheme.PANEL_HOVER);
        }
        IconRenderer.backArrow(g, f, x + 2, y + 1, backSize - 4, backSize - 4, backHover ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);
        if (backHover) GuiRender.tooltip(g, f, "Back", mx, my, sw, sh);

        // Title
        GuiRender.shadowText(g, f, title, x + backSize + 8, y + 4, GuiTheme.ACCENT);

        // Separator
        GuiRender.mcSeparator(g, x, y + backSize + 2, w);

        return y + backSize + 6;
    }

    // ── Mouse Click ──────────────────────────────────────────────────────

    public boolean mouseClicked(GuiFrame frame, double mouseX, double mouseY, int button) {
        int x = frame.contentX();
        int y = frame.contentY();
        int w = frame.contentWidth();
        int h = frame.contentHeight();

        switch (currentView) {
            case CATEGORIES: return clickCategories(x, y, w, h, mouseX, mouseY);
            case FAVORITES: return clickTrackList(x, y, w, h, mouseX, mouseY, button, LibraryManager.getInstance().getFavorites());
            case MOST_REPLAYED: return clickTrackList(x, y, w, h, mouseX, mouseY, button, LibraryManager.getInstance().getMostReplayed());
            case HISTORY: return clickTrackList(x, y, w, h, mouseX, mouseY, button, LibraryManager.getInstance().getTodayHistory());
            case PLAYLIST_LIST: return clickPlaylistList(x, y, w, h, mouseX, mouseY, button);
            case PLAYLIST_DETAIL: return clickTrackList(x, y, w, h, mouseX, mouseY, button,
                    selectedPlaylist != null ? LibraryManager.getInstance().getPlaylist(selectedPlaylist) : Collections.emptyList());
            case GROUP_ARTIST: return clickGroupList(x, y, w, h, mouseX, mouseY, LibraryManager.getInstance().getAutoGroupByArtist(), View.GROUP_ARTIST);
            case GROUP_ALBUM: return clickGroupList(x, y, w, h, mouseX, mouseY, LibraryManager.getInstance().getAutoGroupByAlbum(), View.GROUP_ALBUM);
            case GROUP_SOURCE: return clickGroupList(x, y, w, h, mouseX, mouseY, LibraryManager.getInstance().getAutoGroupBySource(), View.GROUP_SOURCE);
            case GROUP_DETAIL: {
                Map<String, List<TrackRef>> groups = resolveGroupMap();
                List<TrackRef> tracks = selectedGroup != null ? groups.getOrDefault(selectedGroup, Collections.emptyList()) : Collections.emptyList();
                return clickTrackList(x, y, w, h, mouseX, mouseY, button, tracks);
            }
            case LOCAL: return clickLocalView(x, y, w, h, mouseX, mouseY, button);
            default: return false;
        }
    }

    private boolean clickCategories(int x, int y, int w, int h, double mx, double my) {
        int rowX = x + INNER_PAD;
        int rowW = w - INNER_PAD * 2;
        int currentY = y + INNER_PAD + 14; // skip title

        View[] targets = {View.FAVORITES, View.LOCAL, View.PLAYLIST_LIST, View.GROUP_ARTIST, View.GROUP_ALBUM, View.GROUP_SOURCE};

        for (int i = 0; i < targets.length; i++) {
            if (GuiRender.inside(mx, my, rowX, currentY, rowW, ROW_H)) {
                currentView = targets[i];
                scrollOffset = 0;
                return true;
            }
            currentY += ROW_H + 2;
        }
        return false;
    }

    private boolean clickPlaylistList(int x, int y, int w, int h, double mx, double my, int button) {
        int rowX = x + INNER_PAD;
        int rowW = w - INNER_PAD * 2;
        int currentY = y + INNER_PAD;

        // Back button
        int backSize = 16;
        if (GuiRender.inside(mx, my, rowX, currentY, backSize + 4, backSize)) {
            currentView = View.CATEGORIES;
            creatingPlaylist = false;
            newPlaylistName = "";
            scrollOffset = 0;
            return true;
        }
        currentY += backSize + 6;

        // Create button
        int createBtnW = 60;
        if (GuiRender.inside(mx, my, rowX, currentY, createBtnW, ACTION_BAR_H)) {
            if (!creatingPlaylist) {
                creatingPlaylist = true;
                newPlaylistName = "";
            } else {
                // Confirm creation
                if (!newPlaylistName.isBlank()) {
                    LibraryManager.getInstance().createPlaylist(newPlaylistName.trim());
                    creatingPlaylist = false;
                    newPlaylistName = "";
                }
            }
            return true;
        }

        // Inline input click (just focus)
        if (creatingPlaylist && GuiRender.inside(mx, my, rowX + createBtnW + 4, currentY, rowW - createBtnW - 4, CREATE_INPUT_H)) {
            return true;
        }

        currentY += ACTION_BAR_H + SECTION_GAP;

        // Playlist rows
        Set<String> names = LibraryManager.getInstance().getPlaylistNames();
        int idx = 0;
        for (String name : names) {
            if (GuiRender.inside(mx, my, rowX, currentY, rowW - 20, ROW_H)) {
                selectedPlaylist = name;
                currentView = View.PLAYLIST_DETAIL;
                scrollOffset = 0;
                return true;
            }
            // Delete button
            int delX = rowX + rowW - 18;
            int delSize = 14;
            if (GuiRender.inside(mx, my, delX, currentY + (ROW_H - delSize) / 2, delSize, delSize)) {
                LibraryManager.getInstance().deletePlaylist(name);
                return true;
            }
            currentY += ROW_H + 2;
            idx++;
        }
        return false;
    }

    private boolean clickGroupList(int x, int y, int w, int h, double mx, double my, Map<String, List<TrackRef>> groups, View groupType) {
        int rowX = x + INNER_PAD;
        int rowW = w - INNER_PAD * 2;
        int currentY = y + INNER_PAD;

        // Back button
        int backSize = 16;
        if (GuiRender.inside(mx, my, rowX, currentY, backSize + 4, backSize)) {
            currentView = View.CATEGORIES;
            scrollOffset = 0;
            return true;
        }
        currentY += backSize + 6;

        // Group rows
        for (String groupName : groups.keySet()) {
            if (GuiRender.inside(mx, my, rowX, currentY, rowW, ROW_H)) {
                selectedGroup = groupName;
                selectedGroupType = groupType;
                currentView = View.GROUP_DETAIL;
                scrollOffset = 0;
                return true;
            }
            currentY += ROW_H + 2;
        }
        return false;
    }

    private boolean clickTrackList(int x, int y, int w, int h, double mx, double my, int button, Collection<TrackRef> trackCollection) {
        List<TrackRef> tracks = trackCollection instanceof List ? (List<TrackRef>) trackCollection : new ArrayList<>(trackCollection);
        int rowX = x + INNER_PAD;
        int rowW = w - INNER_PAD * 2;
        int currentY = y + INNER_PAD;

        // Back button
        int backSize = 16;
        if (GuiRender.inside(mx, my, rowX, currentY, backSize + 4, backSize)) {
            navigateBack();
            scrollOffset = 0;
            return true;
        }
        currentY += backSize + 6;

        // Action bar
        int btnX = rowX;
        int btnH = ACTION_BAR_H - 4;
        Font font = net.minecraft.client.Minecraft.getInstance().font;

        // Play All
        if (ToolbarButton.isClicked(font, "Play All", btnX, currentY, btnH, mx, my) && !tracks.isEmpty()) {
            PlayerFacade.getInstance().playQueue(tracks, 0);
            return true;
        }
        btnX += ToolbarButton.getWidth(font, "Play All") + ToolbarButton.GAP;

        // Shuffle
        if (ToolbarButton.isClicked(font, "Shuffle", btnX, currentY, btnH, mx, my) && !tracks.isEmpty()) {
            List<TrackRef> shuffled = new ArrayList<>(tracks);
            Collections.shuffle(shuffled);
            PlayerFacade.getInstance().playQueue(shuffled, 0);
            return true;
        }

        currentY += ACTION_BAR_H + SECTION_GAP;

        // Track rows
        int drawY = currentY - (int) scrollOffset;
        for (int i = 0; i < tracks.size(); i++) {
            if (drawY + TrackRow.HEIGHT < currentY) { drawY += TrackRow.HEIGHT + 2; continue; }
            if (drawY > y + h) break;

            if (GuiRender.inside(mx, my, rowX, drawY, rowW, TrackRow.HEIGHT)) {
                TrackRef track = tracks.get(i);
                // Heart click — toggle favorite
                if (TrackRow.isHeartClicked(rowX, drawY, rowW, mx, my)) {
                    LibraryManager.getInstance().toggleFavorite(track);
                    return true;
                }
                // Download click
                if (TrackRow.isDownloadClicked(rowX, drawY, rowW, mx, my)) {
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
                    // Left-click: play from this list
                    PlayerFacade.getInstance().playQueue(tracks, i);
                }
                return true;
            }
            drawY += TrackRow.HEIGHT + 2;
        }
        return false;
    }

    // ── Mouse Scroll ─────────────────────────────────────────────────────

    public boolean mouseScrolled(GuiFrame frame, double mouseX, double mouseY, double amount) {
        // Only scroll in track list or group list views
        if (currentView == View.CATEGORIES) return false;
        scrollOffset -= amount * 20;
        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        return true;
    }

    // ── Key Input ─────────────────────────────────────────────────────────

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (creatingPlaylist) {
            // Escape cancels
            if (keyCode == 256) { // GLFW_KEY_ESCAPE
                creatingPlaylist = false;
                newPlaylistName = "";
                return true;
            }
            // Enter confirms
            if (keyCode == 257) { // GLFW_KEY_ENTER
                if (!newPlaylistName.isBlank()) {
                    LibraryManager.getInstance().createPlaylist(newPlaylistName.trim());
                }
                creatingPlaylist = false;
                newPlaylistName = "";
                return true;
            }
            // Backspace
            if (keyCode == 259) { // GLFW_KEY_BACKSPACE
                if (!newPlaylistName.isEmpty()) {
                    newPlaylistName = newPlaylistName.substring(0, newPlaylistName.length() - 1);
                }
                return true;
            }
            return true; // consume all keys while creating
        }

        // Local search input
        if (currentView == View.LOCAL && localSearchFocused) {
            if (keyCode == 256) { // ESC
                localSearchFocused = false;
                return true;
            }
            if (keyCode == 259) { // Backspace
                if (!localSearchQuery.isEmpty()) {
                    localSearchQuery = localSearchQuery.substring(0, localSearchQuery.length() - 1);
                }
                return true;
            }
            // Consume Enter to prevent it from playing music
            if (keyCode == 257 || keyCode == 335) { // Enter / KP Enter
                return true;
            }
            return true; // consume all keys while search focused
        }

        // Escape goes back
        if (keyCode == 256 && currentView != View.CATEGORIES) {
            navigateBack();
            scrollOffset = 0;
            return true;
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (creatingPlaylist) {
            if (Character.isLetterOrDigit(codePoint) || codePoint == ' ' || codePoint == '_' || codePoint == '-') {
                if (newPlaylistName.length() < 32) {
                    newPlaylistName += codePoint;
                }
            }
            return true;
        }
        // Local search input
        if (currentView == View.LOCAL && localSearchFocused) {
            if (Character.isLetterOrDigit(codePoint) || codePoint == ' ' || codePoint == '_' || codePoint == '-') {
                if (localSearchQuery.length() < 32) {
                    localSearchQuery += codePoint;
                }
            }
            return true;
        }
        return false;
    }

    // ── Local View Click Handler ────────────────────────────────────────

    private boolean clickLocalView(int x, int y, int w, int h, double mx, double my, int button) {
        int rowX = x + INNER_PAD;
        int rowW = w - INNER_PAD * 2;
        int currentY = y + INNER_PAD;

        // Back button
        int backSize = 16;
        if (GuiRender.inside(mx, my, rowX, currentY, backSize + 4, backSize)) {
            currentView = View.CATEGORIES;
            scrollOffset = 0;
            localSearchExpanded = false;
            localSearchFocused = false;
            localSearchQuery = "";
            return true;
        }
        currentY += backSize + 6;

        // Action bar
        int btnX = rowX;
        int btnH = ACTION_BAR_H - 4;
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        List<TrackRef> localTracks = getLocalTrackRefs();
        List<TrackRef> filteredTracks = filterLocalTracks(localTracks);

        // Play All
        if (ToolbarButton.isClicked(font, "Play All", btnX, currentY, btnH, mx, my) && !filteredTracks.isEmpty()) {
            PlayerFacade.getInstance().playQueue(filteredTracks, 0);
            localSearchFocused = false;
            return true;
        }
        btnX += ToolbarButton.getWidth(font, "Play All") + ToolbarButton.GAP;

        // Shuffle
        if (ToolbarButton.isClicked(font, "Shuffle", btnX, currentY, btnH, mx, my) && !filteredTracks.isEmpty()) {
            List<TrackRef> shuffled = new ArrayList<>(filteredTracks);
            Collections.shuffle(shuffled);
            PlayerFacade.getInstance().playQueue(shuffled, 0);
            localSearchFocused = false;
            return true;
        }
        btnX += ToolbarButton.getWidth(font, "Shuffle") + ToolbarButton.GAP;

        // Rescan
        if (ToolbarButton.isClicked(font, "Rescan", btnX, currentY, btnH, mx, my)) {
            LocalMusicService localService = ServiceManager.getLocalMusic();
            if (localService != null) localService.scanAsync();
            localSearchFocused = false;
            return true;
        }
        btnX += ToolbarButton.getWidth(font, "Rescan") + ToolbarButton.GAP;

        // Search toggle
        if (ToolbarButton.isIconClicked(btnX, currentY, btnH, mx, my)) {
            localSearchExpanded = !localSearchExpanded;
            if (localSearchExpanded) { localSearchFocused = true; }
            else { localSearchQuery = ""; localSearchFocused = false; }
            return true;
        }

        // Folder button (right side)
        int folderBtnX = rowX + rowW - ToolbarButton.getIconWidth() - 4;
        if (ToolbarButton.isIconClicked(folderBtnX, currentY, btnH, mx, my)) {
            LocalMusicService localService = ServiceManager.getLocalMusic();
            if (localService != null) {
                com.codexceed.xmusic.XMusic.getPlatform().openFolder(localService.getMusicDirectory());
            }
            return true;
        }

        currentY += ACTION_BAR_H + 2;

        // Search bar clicks
        if (localSearchExpanded) {
            // Clear button
            if (!localSearchQuery.isEmpty()) {
                int clearX = rowX + rowW - 14;
                int clearY = currentY + 3;
                if (GuiRender.inside(mx, my, clearX, clearY, 10, 10)) {
                    localSearchQuery = "";
                    localSearchFocused = true;
                    return true;
                }
            }
            // Click on search bar
            if (GuiRender.inside(mx, my, rowX, currentY, rowW, 16)) {
                localSearchFocused = true;
                return true;
            }
            currentY += 18;
        }

        // Skip scanning indicator (non-clickable)
        LocalMusicService localService = ServiceManager.getLocalMusic();
        if (localService != null && localService.isScanning()) currentY += 12;

        // Track rows
        localSearchFocused = false;
        int drawY = currentY - (int) scrollOffset;
        for (int i = 0; i < filteredTracks.size(); i++) {
            if (drawY + TrackRow.HEIGHT < currentY) { drawY += TrackRow.HEIGHT + 2; continue; }
            if (drawY > y + h) break;

            if (GuiRender.inside(mx, my, rowX, drawY, rowW, TrackRow.HEIGHT)) {
                TrackRef track = filteredTracks.get(i);
                // Heart click
                if (TrackRow.isHeartClicked(rowX, drawY, rowW, mx, my)) {
                    LibraryManager.getInstance().toggleFavorite(track);
                    return true;
                }
                // Download click
                if (TrackRow.isDownloadClicked(rowX, drawY, rowW, mx, my)) {
                    DownloadState dlState = DownloadManager.getInstance().getState(track);
                    if (dlState == DownloadState.NONE || dlState == DownloadState.FAILED) {
                        DownloadManager.getInstance().download(track);
                    }
                    return true;
                }
                if (button == 1) {
                    PlayerFacade.getInstance().addToQueue(track);
                } else {
                    PlayerFacade.getInstance().playQueue(filteredTracks, i);
                }
                return true;
            }
            drawY += TrackRow.HEIGHT + 2;
        }
        return false;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void navigateBack() {
        switch (currentView) {
            case FAVORITES, LOCAL, PLAYLIST_LIST, GROUP_ARTIST, GROUP_ALBUM, GROUP_SOURCE -> currentView = View.CATEGORIES;
            case PLAYLIST_DETAIL -> currentView = View.PLAYLIST_LIST;
            case GROUP_DETAIL -> currentView = selectedGroupType != null ? selectedGroupType : View.CATEGORIES;
            default -> currentView = View.CATEGORIES;
        }
        creatingPlaylist = false;
        newPlaylistName = "";
        localSearchExpanded = false;
        localSearchFocused = false;
        localSearchQuery = "";
    }

    private Map<String, List<TrackRef>> resolveGroupMap() {
        if (selectedGroupType == null) return Collections.emptyMap();
        LibraryManager lib = LibraryManager.getInstance();
        return switch (selectedGroupType) {
            case GROUP_ARTIST -> lib.getAutoGroupByArtist();
            case GROUP_ALBUM -> lib.getAutoGroupByAlbum();
            case GROUP_SOURCE -> lib.getAutoGroupBySource();
            default -> Collections.emptyMap();
        };
    }

    private String formatDuration(long ms) {
        long totalSec = Math.max(0, ms / 1000);
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return min + ":" + (sec < 10 ? "0" : "") + sec;
    }

    // ── Category Definition Helper ───────────────────────────────────────

    @FunctionalInterface
    private interface IconFactory {
        void render(GuiGraphics g, Font f, int x, int y, int w, int h, int color);
    }

    private record CategoryDef(String label, int count, IconFactory icon, View target) {}
}
