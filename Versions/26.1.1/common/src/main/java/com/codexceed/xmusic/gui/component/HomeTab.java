package com.codexceed.xmusic.gui.component;

import com.codexceed.xmusic.gui.layout.GuiFrame;
import com.codexceed.xmusic.gui.render.GuiRender;
import com.codexceed.xmusic.gui.render.IconRenderer;
import com.codexceed.xmusic.gui.theme.GuiTheme;
import com.codexceed.xmusic.library.LibraryManager;
import com.codexceed.xmusic.player.PlayerFacade;
import com.codexceed.xmusic.source.TrackRef;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.*;

/**
 * Spotify-style Home tab with greeting, quick-play grid,
 * horizontal-scroll card sections, and "See all" navigation to Library.
 */
public final class HomeTab {

    // â”€â”€ Layout Constants â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private static final int PAD = 6;
    private static final int SECTION_GAP = 10;
    private static final int CARD_W = 82;
    private static final int CARD_H = 102;
    private static final int CARD_GAP = 6;
    private static final int CARD_ICON_SIZE = 40;
    private static final int TEXT_LINE_H = 9;
    private static final int QUICK_TILE_H = 30;
    private static final int QUICK_TILE_GAP = 3;
    private static final int SECTION_HEADER_H = 18;
    private static final int SEE_ALL_W = 48;
    private static final int ICON_SIZE = 12;
    private static final float SCROLL_LERP = 0.18f; // smooth interpolation speed

    // â”€â”€ Smooth Scroll State â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private double targetVerticalScroll = 0;
    private double smoothVerticalScroll = 0;
    private double maxVerticalScroll = 0;
    private final Map<String, Double> targetSectionScrolls = new HashMap<>();
    private final Map<String, Double> smoothSectionScrolls = new HashMap<>();

    // â”€â”€ Animation â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private long lastFrameTime = System.currentTimeMillis();
    private float eqPhase = 0f; // equalizer animation phase

    // â”€â”€ Navigation callback â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private Runnable navigateToLibrary = null;

    public void setNavigateToLibrary(Runnable callback) {
        this.navigateToLibrary = callback;
    }

    // â”€â”€ Smooth Scroll Helper â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private double lerp(double current, double target, float speed) {
        double delta = target - current;
        if (Math.abs(delta) < 0.5) return target;
        return current + delta * speed;
    }

    private double getSmoothSectionScroll(String id) {
        return smoothSectionScrolls.getOrDefault(id, 0.0);
    }

    private void setTargetSectionScroll(String id, double value) {
        targetSectionScrolls.put(id, value);
        if (!smoothSectionScrolls.containsKey(id)) {
            smoothSectionScrolls.put(id, value);
        }
    }

    // â”€â”€ Render â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public void render(GuiGraphicsExtractor g, Font f, GuiFrame frame, int mx, int my) {
        // Update smooth scroll positions
        long now = System.currentTimeMillis();
        float dt = Math.min(50f, (now - lastFrameTime) / 16.667f); // normalize to ~60fps
        lastFrameTime = now;
        float lerpSpeed = SCROLL_LERP * dt;

        smoothVerticalScroll = lerp(smoothVerticalScroll, targetVerticalScroll, lerpSpeed);
        for (String id : targetSectionScrolls.keySet()) {
            double current = smoothSectionScrolls.getOrDefault(id, 0.0);
            double target = targetSectionScrolls.get(id);
            smoothSectionScrolls.put(id, lerp(current, target, lerpSpeed));
        }

        // Update equalizer phase
        eqPhase += dt * 0.15f;
        if (eqPhase > 6.283f) eqPhase -= 6.283f;

        int x = frame.contentX();
        int y = frame.contentY();
        int w = frame.contentWidth();
        int h = frame.contentHeight();
        int sw = frame.x() + frame.width();
        int sh = frame.y() + frame.height();

        // Scissor clip to content area
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);

        LibraryManager lib = LibraryManager.getInstance();
        int innerX = x + PAD;
        int innerW = w - PAD * 2;
        int currentY = y + PAD - (int) smoothVerticalScroll;

        // 1. Greeting + equalizer animation
        currentY = renderGreeting(g, f, innerX, currentY, innerW);

        // 2. Quick-play grid
        List<TrackRef> quickTracks = getQuickPlayTracks(lib);
        if (!quickTracks.isEmpty()) {
            currentY = renderQuickPlayGrid(g, f, innerX, currentY, innerW, quickTracks, mx, my, sw, sh);
            currentY += SECTION_GAP;
        }

        // 3. Most Played
        List<TrackRef> mostPlayed = lib.getMostReplayed();
        if (!mostPlayed.isEmpty()) {
            Map<String, List<TrackRef>> byArtist = groupByArtist(mostPlayed);
            currentY = renderSection(g, f, innerX, currentY, innerW,
                    "Most Played", IconRenderer::discMostPlayed, byArtist, "most_played", false, mx, my, sw, sh);
            currentY += SECTION_GAP;
        }

        // 4. Recently Played
        List<TrackRef> recent = lib.getTodayHistory();
        if (!recent.isEmpty()) {
            List<TrackRef> last20 = recent.subList(Math.max(0, recent.size() - 20), recent.size());
            Map<String, List<TrackRef>> recentMap = asTrackCards(last20);
            currentY = renderSection(g, f, innerX, currentY, innerW,
                    "Recently Played", IconRenderer::discRecent, recentMap, "recent", true, mx, my, sw, sh);
            currentY += SECTION_GAP;
        }

        // 5. Your Albums
        Map<String, List<TrackRef>> albums = new LinkedHashMap<>(lib.getAutoGroupByAlbum());
        albums.entrySet().removeIf(e -> e.getValue().size() < 2);
        if (!albums.isEmpty()) {
            currentY = renderSection(g, f, innerX, currentY, innerW,
                    "Your Albums", IconRenderer::discAlbums, albums, "albums", false, mx, my, sw, sh);
            currentY += SECTION_GAP;
        }

        // 6. Your Artists
        Map<String, List<TrackRef>> artists = lib.getAutoGroupByArtist();
        if (!artists.isEmpty()) {
            currentY = renderSection(g, f, innerX, currentY, innerW,
                    "Your Artists", IconRenderer::discArtists, artists, "artists", false, mx, my, sw, sh);
            currentY += SECTION_GAP;
        }

        // 7. Playlists
        Set<String> playlistNames = lib.getPlaylistNames();
        if (!playlistNames.isEmpty()) {
            Map<String, List<TrackRef>> playlistMap = new LinkedHashMap<>();
            for (String name : playlistNames) {
                playlistMap.put(name, lib.getPlaylist(name));
            }
            currentY = renderSection(g, f, innerX, currentY, innerW,
                    "Playlists", IconRenderer::discPlaylists, playlistMap, "playlists", false, mx, my, sw, sh);
        }

        // Track max scroll for clamping
        int totalContentH = (currentY + (int) smoothVerticalScroll) - y - PAD;
        maxVerticalScroll = Math.max(0, totalContentH - h + PAD * 2);

        g.disableScissor();
    }

    // â”€â”€ Greeting + Equalizer â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private int renderGreeting(GuiGraphicsExtractor g, Font f, int x, int y, int w) {
        String greeting = getGreeting();
        GuiRender.shadowText(g, f, greeting, x, y, GuiTheme.ACCENT);

        // Small equalizer animation next to greeting
        renderEqualizer(g, x + f.width(greeting) + 8, y + 1, 10, 10);

        y += 12;
        GuiRender.text(g, f, "Your music, your way", x, y, GuiTheme.TEXT_MUTED);
        return y + 14;
    }

    private void renderEqualizer(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int bars = 4;
        int barW = 2;
        int gap = 1;
        int totalW = bars * barW + (bars - 1) * gap;
        int startX = x + (w - totalW) / 2;

        for (int i = 0; i < bars; i++) {
            // Each bar oscillates at a different phase
            float phase = eqPhase + i * 1.2f;
            float heightFactor = 0.3f + 0.7f * (0.5f + 0.5f * (float) Math.sin(phase));
            int barH = (int) (h * heightFactor);
            int barX = startX + i * (barW + gap);
            int barY = y + h - barH;

            // Accent color with slight alpha variation
            int alpha = 0xC0 + (int) (0x3F * heightFactor);
            int color = (alpha << 24) | (GuiTheme.ACCENT & 0x00FFFFFF);
            g.fill(barX, barY, barX + barW, barY + barH, color);
        }
    }

    private String getGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return "Good morning";
        if (hour < 18) return "Good afternoon";
        return "Good evening";
    }

    // â”€â”€ Quick-Play Grid â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private int renderQuickPlayGrid(GuiGraphicsExtractor g, Font f, int x, int y, int w,
                                     List<TrackRef> tracks, int mx, int my, int sw, int sh) {
        int tileW = 120;
        int cols = Math.max(1, (w + QUICK_TILE_GAP) / (tileW + QUICK_TILE_GAP));
        tileW = (w - (cols - 1) * QUICK_TILE_GAP) / cols;

        int maxTiles = Math.min(tracks.size(), cols * 2);
        for (int i = 0; i < maxTiles; i++) {
            int col = i % cols;
            int row = i / cols;
            int tileX = x + col * (tileW + QUICK_TILE_GAP);
            int tileY = y + row * (QUICK_TILE_H + QUICK_TILE_GAP);

            TrackRef track = tracks.get(i);
            boolean hovered = GuiRender.inside(mx, my, tileX, tileY, tileW, QUICK_TILE_H);

            int bg = hovered ? GuiTheme.QUICK_TILE_HOVER : GuiTheme.QUICK_TILE_BG;
            g.fill(tileX, tileY, tileX + tileW, tileY + QUICK_TILE_H, bg);
            GuiRender.bevelHover(g, tileX, tileY, tileW, QUICK_TILE_H, false, hovered);

            // Accent left border on hover
            if (hovered) {
                g.fill(tileX, tileY, tileX + 2, tileY + QUICK_TILE_H, GuiTheme.ACCENT);
            }

            int iconX = tileX + 6;
            int iconY = tileY + (QUICK_TILE_H - ICON_SIZE) / 2;
            IconRenderer.musicNote(g, f, iconX, iconY, ICON_SIZE, ICON_SIZE,
                    hovered ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);

            int textX = iconX + ICON_SIZE + 4;
            int textW = tileW - ICON_SIZE - 16;
            String label = track.getTitle() + " \u00B7 " + track.getArtist();
            GuiRender.truncated(g, f, label, textX, tileY + (QUICK_TILE_H - 8) / 2, textW,
                    hovered ? GuiTheme.TEXT : GuiTheme.TEXT_SOFT);

        }

        int rows = Math.min(2, (maxTiles + cols - 1) / cols);
        return y + rows * QUICK_TILE_H + (rows - 1) * QUICK_TILE_GAP + 4;
    }

    // â”€â”€ Section (horizontal card row) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private int renderSection(GuiGraphicsExtractor g, Font f, int x, int y, int w,
                              String title, SectionIcon icon, Map<String, List<TrackRef>> groups,
                              String sectionId, boolean isTrackCards, int mx, int my, int sw, int sh) {
        // Section header
        icon.render(g, f, x, y + 3, ICON_SIZE, ICON_SIZE, GuiTheme.ACCENT);
        GuiRender.shadowText(g, f, title, x + ICON_SIZE + 4, y + 3, GuiTheme.SECTION_HEADER);

        // "See all >" link
        int seeAllX = x + w - SEE_ALL_W;
        boolean seeAllHover = GuiRender.inside(mx, my, seeAllX, y, SEE_ALL_W, SECTION_HEADER_H);
        GuiRender.text(g, f, "See all \u25B8", seeAllX, y + 3,
                seeAllHover ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);
        if (seeAllHover) {
            GuiRender.tooltip(g, f, "View in Library", mx, my, sw, sh);
        }

        y += SECTION_HEADER_H + 4;

        // Card row â€” scissor clip to section width
        int rowY = y;
        int scroll = (int) getSmoothSectionScroll(sectionId);
        int totalCardsW = groups.size() * (CARD_W + CARD_GAP) - CARD_GAP;
        boolean needsScroll = totalCardsW > w;

        if (needsScroll) {
            g.enableScissor(x, rowY, x + w, rowY + CARD_H + 2);
        }

        int cardX = x - scroll;
        for (Map.Entry<String, List<TrackRef>> entry : groups.entrySet()) {
            if (cardX + CARD_W > x - CARD_W && cardX < x + w + CARD_W) {
                renderCard(g, f, cardX, rowY, entry.getKey(), entry.getValue(), isTrackCards, icon, mx, my, sw, sh);
            }
            cardX += CARD_W + CARD_GAP;
        }

        if (needsScroll) {
            g.disableScissor();
        }

        // Smooth scroll arrows with gradient fade (only if scrollable)
        if (needsScroll) {
            double rawScroll = getSmoothSectionScroll(sectionId);
            if (rawScroll > 2) {
                for (int i = 0; i < 12; i++) {
                    int alpha = (int) (0xD0 * (1.0 - i / 12.0));
                    g.fill(x + i, rowY, x + i + 1, rowY + CARD_H, (alpha << 24) | (GuiTheme.PANEL & 0x00FFFFFF));
                }
                GuiRender.text(g, f, "\u25C0", x + 2, rowY + CARD_H / 2 - 4, GuiTheme.ACCENT);
            }
            if (rawScroll + w < totalCardsW - 2) {
                for (int i = 0; i < 12; i++) {
                    int alpha = (int) (0xD0 * (i / 12.0));
                    g.fill(x + w - 12 + i, rowY, x + w - 11 + i, rowY + CARD_H, (alpha << 24) | (GuiTheme.PANEL & 0x00FFFFFF));
                }
                GuiRender.text(g, f, "\u25B6", x + w - 9, rowY + CARD_H / 2 - 4, GuiTheme.ACCENT);
            }
        }

        return rowY + CARD_H + 4;
    }

    // â”€â”€ Card â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void renderCard(GuiGraphicsExtractor g, Font f, int x, int y, String label,
                            List<TrackRef> tracks, boolean isTrackCards,
                            SectionIcon sectionIcon,
                            int mx, int my, int sw, int sh) {
        boolean hovered = GuiRender.inside(mx, my, x, y, CARD_W, CARD_H);

        // Check if any track in this card is currently playing
        TrackRef currentPlaying = PlayerFacade.getInstance().snapshot().getCurrentTrack();
        boolean isActive = false;
        if (currentPlaying != null) {
            for (TrackRef t : tracks) {
                if (t.getId().equals(currentPlaying.getId())) {
                    isActive = true;
                    break;
                }
            }
        }

        // Card background
        int bg = hovered ? GuiTheme.CARD_HOVER : (isActive ? 0xFF1A1225 : GuiTheme.CARD_BG);
        g.fill(x, y, x + CARD_W, y + CARD_H, bg);
        GuiRender.bevelHover(g, x, y, CARD_W, CARD_H, false, hovered || isActive);

        // Active indicator: accent glow border + top accent line
        if (isActive) {
            GuiRender.accentGlow(g, x, y, CARD_W, CARD_H);
            g.fill(x + 1, y, x + CARD_W - 1, y + 2, GuiTheme.ACCENT);
        }

        // Hover accent glow
        if (hovered && !isActive) {
            GuiRender.glowRect(g, x, y, CARD_W, CARD_H);
        }

        // Icon area â€” dark inset with subtle border
        int iconPad = 5;
        int iconX = x + iconPad;
        int iconY = y + iconPad;
        int iconBgW = CARD_W - iconPad * 2;
        int iconBgH = CARD_ICON_SIZE + 8;
        g.fill(iconX, iconY, iconX + iconBgW, iconY + iconBgH, GuiTheme.PANEL_DARK);
        GuiRender.bevel(g, iconX, iconY, iconBgW, iconBgH, true);

        int iconColor = hovered ? GuiTheme.ACCENT : (isActive ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);
        if (isActive) {
            // Active card: show music note with glow
            GuiRender.accentGlow(g, iconX, iconY, iconBgW, iconBgH);
            IconRenderer.musicNote(g, f, iconX + (iconBgW - CARD_ICON_SIZE) / 2,
                    iconY + (iconBgH - CARD_ICON_SIZE) / 2,
                    CARD_ICON_SIZE, CARD_ICON_SIZE, GuiTheme.ACCENT);
        } else {
            // Category-specific disc icon
            sectionIcon.render(g, f, iconX + (iconBgW - CARD_ICON_SIZE) / 2,
                    iconY + (iconBgH - CARD_ICON_SIZE) / 2,
                    CARD_ICON_SIZE, CARD_ICON_SIZE, iconColor);
        }

        // Play overlay on hover
        if (hovered && !isActive) {
            int playSize = 20;
            int playX = iconX + (iconBgW - playSize) / 2;
            int playY = iconY + (iconBgH - playSize) / 2;
            g.fill(iconX, iconY, iconX + iconBgW, iconY + iconBgH, 0x80000000);
            IconRenderer.play(g, f, playX, playY, playSize, playSize, GuiTheme.ACCENT);
        }

        // Text area below icon â€” up to 2 lines title + 1 line subtitle
        int textY = iconY + iconBgH + 4;
        int textX = x + 4;
        int textW = CARD_W - 8;
        int titleColor = isActive ? GuiTheme.ACCENT : (hovered ? GuiTheme.TEXT : GuiTheme.TEXT_SOFT);

        // Render title across up to 2 lines
        String remaining = label;
        for (int line = 0; line < 2; line++) {
            if (remaining.isEmpty()) break;
            if (f.width(remaining) <= textW) {
                GuiRender.text(g, f, remaining, textX, textY + line * TEXT_LINE_H, titleColor);
                remaining = "";
                break;
            }
            String fits = f.plainSubstrByWidth(remaining, textW);
            if (fits.isEmpty()) break;

            if (line < 1) {
                int breakAt = fits.lastIndexOf(' ');
                if (breakAt > 0) {
                    GuiRender.text(g, f, fits.substring(0, breakAt), textX, textY + line * TEXT_LINE_H, titleColor);
                    remaining = remaining.substring(breakAt).trim();
                } else {
                    GuiRender.text(g, f, fits, textX, textY + line * TEXT_LINE_H, titleColor);
                    remaining = remaining.substring(fits.length()).trim();
                }
            } else {
                String after = remaining.substring(fits.length()).trim();
                if (!after.isEmpty()) {
                    GuiRender.truncated(g, f, remaining, textX, textY + line * TEXT_LINE_H, textW, titleColor);
                    remaining = "";
                } else {
                    GuiRender.text(g, f, fits, textX, textY + line * TEXT_LINE_H, titleColor);
                    remaining = "";
                }
            }
        }

        // Subtitle: artist for tracks, track count for collections
        int subY = textY + 2 * TEXT_LINE_H + 2;
        if (isTrackCards) {
            String artist = tracks.get(0).getArtist();
            if (artist == null || artist.isBlank()) artist = "Unknown";
            GuiRender.truncated(g, f, artist, textX, subY, textW, GuiTheme.ACCENT);
        } else {
            String subtitle = tracks.size() + " track" + (tracks.size() != 1 ? "s" : "");
            GuiRender.truncated(g, f, subtitle, textX, subY, textW, GuiTheme.TEXT_MUTED);
        }
    }

    // â”€â”€ Mouse â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public boolean mouseClicked(GuiFrame frame, double mx, double my, int button) {
        int x = frame.contentX();
        int y = frame.contentY();
        int w = frame.contentWidth();
        int h = frame.contentHeight();

        if (!GuiRender.inside(mx, my, x, y, w, h)) return false;

        LibraryManager lib = LibraryManager.getInstance();
        int innerX = x + PAD;
        int innerW = w - PAD * 2;
        int currentY = y + PAD - (int) smoothVerticalScroll;

        // Greeting
        currentY += 26;

        // Quick-play grid
        List<TrackRef> quickTracks = getQuickPlayTracks(lib);
        if (!quickTracks.isEmpty()) {
            int tileW = 120;
            int cols = Math.max(1, (innerW + QUICK_TILE_GAP) / (tileW + QUICK_TILE_GAP));
            tileW = (innerW - (cols - 1) * QUICK_TILE_GAP) / cols;
            int maxTiles = Math.min(quickTracks.size(), cols * 2);
            for (int i = 0; i < maxTiles; i++) {
                int col = i % cols;
                int row = i / cols;
                int tileX = innerX + col * (tileW + QUICK_TILE_GAP);
                int tileY = currentY + row * (QUICK_TILE_H + QUICK_TILE_GAP);
                if (GuiRender.inside(mx, my, tileX, tileY, tileW, QUICK_TILE_H)) {
                    TrackRef track = quickTracks.get(i);
                    if (button == 1) {
                        PlayerFacade.getInstance().addToQueue(track);
                    } else {
                        PlayerFacade.getInstance().playQueue(Collections.singletonList(track), 0);
                    }
                    return true;
                }
            }
            int rows = Math.min(2, (maxTiles + cols - 1) / cols);
            currentY += rows * QUICK_TILE_H + (rows - 1) * QUICK_TILE_GAP + 4 + SECTION_GAP;
        }

        if (clickSections(lib, innerX, innerW, currentY, mx, my, button)) return true;
        return false;
    }

    private boolean clickSections(LibraryManager lib, int x, int w,
                                   int currentY, double mx, double my, int button) {
        List<TrackRef> mostPlayed = lib.getMostReplayed();
        if (!mostPlayed.isEmpty()) {
            Boolean result = clickSection(x, w, currentY, groupByArtist(mostPlayed), "most_played", mx, my, button);
            if (result != null) return result;
            currentY += SECTION_HEADER_H + 4 + CARD_H + 4 + SECTION_GAP;
        }

        List<TrackRef> recent = lib.getTodayHistory();
        if (!recent.isEmpty()) {
            List<TrackRef> last20 = recent.subList(Math.max(0, recent.size() - 20), recent.size());
            Boolean result = clickSection(x, w, currentY, asTrackCards(last20), "recent", mx, my, button);
            if (result != null) return result;
            currentY += SECTION_HEADER_H + 4 + CARD_H + 4 + SECTION_GAP;
        }

        Map<String, List<TrackRef>> albums = new LinkedHashMap<>(lib.getAutoGroupByAlbum());
        albums.entrySet().removeIf(e -> e.getValue().size() < 2);
        if (!albums.isEmpty()) {
            Boolean result = clickSection(x, w, currentY, albums, "albums", mx, my, button);
            if (result != null) return result;
            currentY += SECTION_HEADER_H + 4 + CARD_H + 4 + SECTION_GAP;
        }

        Map<String, List<TrackRef>> artists = lib.getAutoGroupByArtist();
        if (!artists.isEmpty()) {
            Boolean result = clickSection(x, w, currentY, artists, "artists", mx, my, button);
            if (result != null) return result;
            currentY += SECTION_HEADER_H + 4 + CARD_H + 4 + SECTION_GAP;
        }

        Set<String> playlistNames = lib.getPlaylistNames();
        if (!playlistNames.isEmpty()) {
            Map<String, List<TrackRef>> playlistMap = new LinkedHashMap<>();
            for (String name : playlistNames) {
                playlistMap.put(name, lib.getPlaylist(name));
            }
            Boolean result = clickSection(x, w, currentY, playlistMap, "playlists", mx, my, button);
            if (result != null) return result;
        }

        return false;
    }

    private Boolean clickSection(int x, int w, int currentY,
                                  Map<String, List<TrackRef>> groups, String sectionId,
                                  double mx, double my, int button) {
        // "See all" click
        int seeAllX = x + w - SEE_ALL_W;
        if (GuiRender.inside(mx, my, seeAllX, currentY, SEE_ALL_W, SECTION_HEADER_H)) {
            if (navigateToLibrary != null) navigateToLibrary.run();
            return true;
        }

        currentY += SECTION_HEADER_H + 4;

        // Card clicks â€” use smooth scroll position
        int scroll = (int) getSmoothSectionScroll(sectionId);
        int cardX = x - scroll;
        for (Map.Entry<String, List<TrackRef>> entry : groups.entrySet()) {
            if (cardX + CARD_W > x && cardX < x + w) {
                if (GuiRender.inside(mx, my, cardX, currentY, CARD_W, CARD_H)) {
                    List<TrackRef> tracks = entry.getValue();
                    if (!tracks.isEmpty()) {
                        if (button == 1) {
                            PlayerFacade.getInstance().addToQueue(tracks);
                        } else {
                            PlayerFacade.getInstance().playQueue(tracks, 0);
                        }
                    }
                    return true;
                }
            }
            cardX += CARD_W + CARD_GAP;
        }

        return null;
    }

    // â”€â”€ Scroll â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public boolean mouseScrolled(GuiFrame frame, double mx, double my, double amount) {
        int x = frame.contentX();
        int y = frame.contentY();
        int w = frame.contentWidth();
        int h = frame.contentHeight();

        if (!GuiRender.inside(mx, my, x, y, w, h)) return false;

        LibraryManager lib = LibraryManager.getInstance();
        int innerX = x + PAD;
        int innerW = w - PAD * 2;
        int currentY = y + PAD - (int) smoothVerticalScroll;
        currentY += 26;

        List<TrackRef> quickTracks = getQuickPlayTracks(lib);
        if (!quickTracks.isEmpty()) {
            int cols = Math.max(1, (innerW + QUICK_TILE_GAP) / (120 + QUICK_TILE_GAP));
            int maxTiles = Math.min(quickTracks.size(), cols * 2);
            int rows = Math.min(2, (maxTiles + cols - 1) / cols);
            currentY += rows * QUICK_TILE_H + (rows - 1) * QUICK_TILE_GAP + 4 + SECTION_GAP;
        }

        if (scrollSections(lib, innerX, innerW, currentY, mx, my, amount)) return true;

        // Vertical scroll â€” set target, smooth lerp handles the rest
        targetVerticalScroll -= amount * 20;
        if (targetVerticalScroll < 0) targetVerticalScroll = 0;
        if (targetVerticalScroll > maxVerticalScroll) targetVerticalScroll = maxVerticalScroll;
        return true;
    }

    private boolean scrollSections(LibraryManager lib, int x, int w,
                                    int currentY, double mx, double my, double amount) {
        List<TrackRef> mostPlayed = lib.getMostReplayed();
        if (!mostPlayed.isEmpty()) {
            if (tryScrollSection(x, currentY, w, groupByArtist(mostPlayed), "most_played", mx, my, amount)) return true;
            currentY += SECTION_HEADER_H + 4 + CARD_H + 4 + SECTION_GAP;
        }

        List<TrackRef> recent = lib.getTodayHistory();
        if (!recent.isEmpty()) {
            List<TrackRef> last20 = recent.subList(Math.max(0, recent.size() - 20), recent.size());
            if (tryScrollSection(x, currentY, w, asTrackCards(last20), "recent", mx, my, amount)) return true;
            currentY += SECTION_HEADER_H + 4 + CARD_H + 4 + SECTION_GAP;
        }

        Map<String, List<TrackRef>> albums = new LinkedHashMap<>(lib.getAutoGroupByAlbum());
        albums.entrySet().removeIf(e -> e.getValue().size() < 2);
        if (!albums.isEmpty()) {
            if (tryScrollSection(x, currentY, w, albums, "albums", mx, my, amount)) return true;
            currentY += SECTION_HEADER_H + 4 + CARD_H + 4 + SECTION_GAP;
        }

        Map<String, List<TrackRef>> artists = lib.getAutoGroupByArtist();
        if (!artists.isEmpty()) {
            if (tryScrollSection(x, currentY, w, artists, "artists", mx, my, amount)) return true;
            currentY += SECTION_HEADER_H + 4 + CARD_H + 4 + SECTION_GAP;
        }

        Set<String> playlistNames = lib.getPlaylistNames();
        if (!playlistNames.isEmpty()) {
            Map<String, List<TrackRef>> playlistMap = new LinkedHashMap<>();
            for (String name : playlistNames) {
                playlistMap.put(name, lib.getPlaylist(name));
            }
            if (tryScrollSection(x, currentY, w, playlistMap, "playlists", mx, my, amount)) return true;
        }

        return false;
    }

    private boolean tryScrollSection(int x, int y, int w, Map<String, List<TrackRef>> groups,
                                      String sectionId, double mx, double my, double amount) {
        int rowY = y + SECTION_HEADER_H + 4;
        if (!GuiRender.inside(mx, my, x, rowY, w, CARD_H)) return false;

        int totalCardsW = groups.size() * (CARD_W + CARD_GAP) - CARD_GAP;
        int maxScroll = Math.max(0, totalCardsW - w);

        // No horizontal scroll needed if all cards fit
        if (maxScroll <= 0) return false;

        double scroll = targetSectionScrolls.getOrDefault(sectionId, 0.0);
        double newScroll = scroll - amount * 35;

        // Clamp to bounds
        boolean wasAtBound = false;
        if (newScroll < 0) {
            wasAtBound = scroll <= 0;
            newScroll = 0;
        } else if (newScroll > maxScroll) {
            wasAtBound = scroll >= maxScroll;
            newScroll = maxScroll;
        }

        setTargetSectionScroll(sectionId, newScroll);

        // If we were already at the boundary, don't consume â€” let vertical scroll handle it
        return !wasAtBound;
    }

    // â”€â”€ Data Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private List<TrackRef> getQuickPlayTracks(LibraryManager lib) {
        List<TrackRef> result = new ArrayList<>();
        Set<TrackRef> seen = new HashSet<>();
        for (TrackRef t : lib.getMostReplayed()) {
            if (seen.add(t)) result.add(t);
            if (result.size() >= 8) break;
        }
        for (TrackRef t : lib.getFavorites()) {
            if (seen.add(t)) result.add(t);
            if (result.size() >= 8) break;
        }
        return result;
    }

    private Map<String, List<TrackRef>> groupByArtist(List<TrackRef> tracks) {
        Map<String, List<TrackRef>> map = new LinkedHashMap<>();
        for (TrackRef t : tracks) {
            String key = t.getArtist() != null && !t.getArtist().isBlank() ? t.getArtist() : "(Unknown)";
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        return map;
    }

    private Map<String, List<TrackRef>> asTrackCards(List<TrackRef> tracks) {
        Map<String, List<TrackRef>> map = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (TrackRef t : tracks) {
            String key = t.getTitle() + "|" + t.getSourceId();
            if (seen.add(key)) {
                map.put(t.getTitle(), Collections.singletonList(t));
            }
        }
        return map;
    }

    // â”€â”€ Section Icon Interface â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @FunctionalInterface
    private interface SectionIcon {
        void render(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c);
    }
}
