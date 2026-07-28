package com.codexceed.xmusic.gui.component;

import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.gui.layout.GuiFrame;
import com.codexceed.xmusic.gui.render.ArtworkRenderer;
import com.codexceed.xmusic.gui.render.GuiRender;
import com.codexceed.xmusic.gui.render.HoverTracker;
import com.codexceed.xmusic.gui.render.IconRenderer;
import com.codexceed.xmusic.gui.theme.GuiTheme;
import com.codexceed.xmusic.gui.util.AnimationHelper;
import com.codexceed.xmusic.download.DownloadManager;
import com.codexceed.xmusic.download.DownloadState;
import com.codexceed.xmusic.library.LibraryManager;
import com.codexceed.xmusic.player.PlayerFacade;
import com.codexceed.xmusic.player.PlayerState;
import com.codexceed.xmusic.service.ServiceManager;
import com.codexceed.xmusic.source.PlaybackType;
import com.codexceed.xmusic.source.TrackRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class PlayerBar {
    private static final int BTN_W = 22;
    private static final int PLAY_BTN_W = 28;
    private static final int BTN_H = 22;
    private static final int BAR_COUNT = 36;
    private static final int BAR_MAX_H = 14;
    private static final int BAR_MIN_H = 1;
    private static final long START_TIME = System.currentTimeMillis();
    private final float[] barHeights = new float[BAR_COUNT];
    private final float[] barVelocity = new float[BAR_COUNT];

    private boolean draggingProgress = false;
    private boolean draggingVolume = false;

    // Playlist popup state
    private boolean showPlaylistPopup = false;
    private boolean creatingNewPlaylist = false;
    private String newPlaylistName = "";
    private String playlistAddedMsg = "";
    private long playlistAddedMsgTime = 0;

    public void render(GuiGraphics graphics, Font font, GuiFrame frame, int mouseX, int mouseY) {
        int x = frame.playerX();
        int y = frame.playerY();
        int w = frame.playerWidth();
        int h = frame.playerHeight();

        // 1. Footer Background: premium gradient panel
        GuiRender.mcPanelGradient(graphics, x, y, w, h);
        // Depth separation shadow at top edge
        GuiRender.depthShadow(graphics, x + 1, y, w - 2);

        PlayerState state = PlayerFacade.getInstance().snapshot();
        TrackRef track = state.getCurrentTrack();

        long pos = state.getPositionMs();
        long dur = state.getDurationMs();
        float progressPct = dur > 0 ? (float) pos / dur : 0f;
        if (progressPct > 1f) progressPct = 1f;

        // Hide progress bar for all NATIVE tracks (local + downloaded)
        boolean isNativeTrack = track != null && track.getPlaybackType() == PlaybackType.NATIVE;

        // 2. Progress Bar (Top Border) - Draggable — only for non-NATIVE tracks
        if (!isNativeTrack) {
            int progressW = w;
            int progressX = x;
            int progressY = y;
            boolean hoverProgress = GuiRender.inside(mouseX, mouseY, progressX, progressY, progressW, 12);

            // Progress bar: MC inset well track
            GuiRender.mcWell(graphics, progressX, progressY + 1, progressW, 3);
            int progFilled = (int)(progressW * progressPct);
            graphics.fill(progressX + 1, progressY + 2, progressX + progFilled, progressY + 3, GuiTheme.ACCENT);

            // Knob: MC raised bevel style
            if (hoverProgress || draggingProgress) {
                GuiRender.mcButton(graphics, progressX + progFilled - 3, progressY - 1, 6, 5, true, false);
            } else if (progFilled > 0) {
                graphics.fill(progressX + progFilled - 2, progressY, progressX + progFilled + 2, progressY + 4, GuiTheme.TEXT_SOFT);
            }

            // Time labels right below progress bar
            String posStr = formatDuration(pos);
            String durStr = formatDuration(dur);
            GuiRender.shadowText(graphics, font, posStr, x + 8, y + 6, GuiTheme.TEXT_MUTED);
            GuiRender.shadowText(graphics, font, durStr, x + w - font.width(durStr) - 8, y + 6, GuiTheme.TEXT_MUTED);
        }
        boolean compact = frame.compact();

        // 3. Center (Playback Controls)
        int btnW = compact ? 16 : 22;
        int playBtnW = compact ? 20 : 28;
        int btnH = compact ? 16 : 22;
        int hSpacing = compact ? 4 : 10;
        int controlsW = (btnW * 4) + playBtnW + (hSpacing * 4);
        int controlsX = x + w / 2 - controlsW / 2;
        int controlsY = y + (compact ? 37 : 34); 

        int bx = controlsX;
        boolean hoverBack = false;
        float hoverBackLerp = 0;
        boolean hoverPrev = false;
        float hoverPrevLerp = 0;
        boolean hoverPlay = false;
        float hoverPlayLerp = 0;
        boolean hoverNext = false;
        float hoverNextLerp = 0;
        boolean hoverFwd = false;
        float hoverFwdLerp = 0;

        // Button 1: Previous Track (skipBack)
        hoverBack = GuiRender.inside(mouseX, mouseY, bx, controlsY, btnW, btnH);
        hoverBackLerp = HoverTracker.tick("pb_skipback", hoverBack);
        renderIconButtonSmooth(graphics, font, bx, controlsY, btnW, btnH, false, hoverBackLerp, IconRenderer::skipBack);
        bx += btnW + hSpacing;

        // Button 2: Rewind 5s (prev)
        hoverPrev = GuiRender.inside(mouseX, mouseY, bx, controlsY, btnW, btnH);
        hoverPrevLerp = HoverTracker.tick("pb_prev", hoverPrev);
        renderIconButtonSmooth(graphics, font, bx, controlsY, btnW, btnH, false, hoverPrevLerp, IconRenderer::prev);
        bx += btnW + hSpacing;

        // Button 3: Play/Pause
        hoverPlay = GuiRender.inside(mouseX, mouseY, bx, controlsY, playBtnW, btnH);
        hoverPlayLerp = HoverTracker.tick("pb_play", hoverPlay);
        if (state.isPlaying()) {
            renderIconButtonSmooth(graphics, font, bx, controlsY, playBtnW, btnH, true, hoverPlayLerp, IconRenderer::pause);
            GuiRender.accentGlow(graphics, bx, controlsY, playBtnW, btnH);
        } else {
            renderIconButtonSmooth(graphics, font, bx, controlsY, playBtnW, btnH, false, hoverPlayLerp, IconRenderer::play);
        }
        bx += playBtnW + hSpacing;

        // Button 4: Forward 5s (next)
        hoverNext = GuiRender.inside(mouseX, mouseY, bx, controlsY, btnW, btnH);
        hoverNextLerp = HoverTracker.tick("pb_next", hoverNext);
        renderIconButtonSmooth(graphics, font, bx, controlsY, btnW, btnH, false, hoverNextLerp, IconRenderer::next);
        bx += btnW + hSpacing;

        // Button 5: Next Track (skipForward)
        hoverFwd = GuiRender.inside(mouseX, mouseY, bx, controlsY, btnW, btnH);
        hoverFwdLerp = HoverTracker.tick("pb_skipfwd", hoverFwd);
        renderIconButtonSmooth(graphics, font, bx, controlsY, btnW, btnH, false, hoverFwdLerp, IconRenderer::skipForward);

        // Tooltips for playback controls only
        String tooltipText = null;
        if (hoverBack) tooltipText = "Previous Track";
        else if (hoverPrev) tooltipText = "Rewind 5s";
        else if (hoverPlay) tooltipText = state.isPlaying() ? "Pause" : "Play";
        else if (hoverNext) tooltipText = "Forward 5s";
        else if (hoverFwd) tooltipText = "Next Track";
        if (tooltipText != null) {
            GuiRender.tooltip(graphics, font, tooltipText, mouseX, mouseY, frame.x() + frame.width(), frame.y() + frame.height());
        }

        // 4. Right Side (Volume, Loop, Autoplay)
        int rightEdge = x + w - 24; 
        float vol = state.getVolume();
        String pctStr = String.format("%d%%", (int)(vol * 100));
        int volY = y + (compact ? 43 : 48);

        int pctSlotW = compact ? 22 : font.width("100%");
        int pctX = rightEdge - pctSlotW;
        GuiRender.text(graphics, font, pctStr, pctX + (pctSlotW - font.width(pctStr)), volY - 3, GuiTheme.TEXT_SOFT);

        int volW = compact ? 30 : 70;
        int volX = pctX - (compact ? 4 : 8) - volW;
        int volIconSize = compact ? 10 : 14;
        int volIconX = volX - volIconSize - (compact ? 2 : 4);
        int volIconY = volY - (compact ? 7 : 10);

        IconRenderer.volume(graphics, font, volIconX, volIconY, volIconSize, volIconSize, vol == 0f ? GuiTheme.TEXT_MUTED : GuiTheme.TEXT_SOFT);

        // Volume: MC inset well track
        boolean hoverVol = GuiRender.inside(mouseX, mouseY, volX - 2, volY - 6, volW + 4, 12);
        GuiRender.mcWell(graphics, volX, volY, volW, 3);
        int volFilled = (int)(volW * vol);
        graphics.fill(volX + 1, volY + 1, volX + volFilled, volY + 2, GuiTheme.ACCENT);

        if (hoverVol || draggingVolume) {
            GuiRender.mcButton(graphics, volX + volFilled - (compact ? 2 : 3), volY - (compact ? 2 : 3), compact ? 4 : 6, compact ? 4 : 6, true, false);
        } else if (volFilled > 0) {
            graphics.fill(volX + volFilled - 1, volY - 1, volX + volFilled + 1, volY + 3, GuiTheme.TEXT_SOFT);
        }

        int btnSize = compact ? 16 : 54;
        int autoplayX = volIconX - btnSize - 6;
        int loopX = autoplayX - btnSize - 6;

        if (compact) {
            // Render compact loop button (icon only)
            boolean hoverLoop = GuiRender.inside(mouseX, mouseY, loopX, volY - 8, btnSize, btnSize);
            float hoverLoopLerp = HoverTracker.tick("pb_loop_c", hoverLoop);
            renderIconButtonSmooth(graphics, font, loopX, volY - 8, btnSize, btnSize, state.isLooping(), hoverLoopLerp, IconRenderer::loop);
            if (hoverLoop) {
                GuiRender.tooltip(graphics, font, "Loop: " + state.getLoopDisplay(), mouseX, mouseY, frame.x() + frame.width(), frame.y() + frame.height());
            }

            // Render compact autoplay button (icon only)
            boolean hoverAuto = GuiRender.inside(mouseX, mouseY, autoplayX, volY - 8, btnSize, btnSize);
            float hoverAutoLerp = HoverTracker.tick("pb_auto_c", hoverAuto);
            renderIconButtonSmooth(graphics, font, autoplayX, volY - 8, btnSize, btnSize, state.isAutoplay(), hoverAutoLerp, IconRenderer::autoPlay);
            if (hoverAuto) {
                GuiRender.tooltip(graphics, font, "Autoplay: " + (state.isAutoplay() ? "ON" : "OFF"), mouseX, mouseY, frame.x() + frame.width(), frame.y() + frame.height());
            }
        } else {
            // Non-compact layout: badges
            int topBtnY = y + 20;
            int topBtnW = 54;
            int topBtnH = 16;
            int badgeAutoplayX = rightEdge - topBtnW;
            int badgeLoopX = badgeAutoplayX - topBtnW - 8;
            
            String loopLabel = state.getLoopDisplay();
            String autoLabel = state.isAutoplay() ? "ON" : "OFF";
            
            boolean hoverLoop = GuiRender.inside(mouseX, mouseY, badgeLoopX, topBtnY, topBtnW, topBtnH);
            boolean hoverAuto = GuiRender.inside(mouseX, mouseY, badgeAutoplayX, topBtnY, topBtnW, topBtnH);
            
            renderIconBadge(graphics, font, loopLabel, badgeLoopX, topBtnY, topBtnW, topBtnH, state.isLooping(), hoverLoop, IconRenderer::loop);
            renderIconBadge(graphics, font, autoLabel, badgeAutoplayX, topBtnY, topBtnW, topBtnH, state.isAutoplay(), hoverAuto, IconRenderer::autoPlay);

            if (hoverLoop) {
                GuiRender.tooltip(graphics, font, "Loop: " + loopLabel, mouseX, mouseY, frame.x() + frame.width(), frame.y() + frame.height());
            } else if (hoverAuto) {
                GuiRender.tooltip(graphics, font, "Autoplay: " + autoLabel, mouseX, mouseY, frame.x() + frame.width(), frame.y() + frame.height());
            }
        }

        // 5. Draw track info (Left) — two-line layout: title on top, artist + action buttons below
        int artW = compact ? 32 : 64;
        int artH = compact ? 24 : 36;
        int artX = x + 10;
        int artY = y + (h - artH) / 2;

        // Music logo box — show album art if available, else track-aware placeholder
        GuiRender.mcWell(graphics, artX - 1, artY - 1, artW + 2, artH + 2);
        if (track != null) {
            if (state.isPlaying()) {
                GuiRender.accentGlow(graphics, artX, artY, artW, artH);
            }
            ArtworkRenderer.renderArtwork(graphics, track, artX, artY, artW, artH);
        } else {
            ArtworkRenderer.renderPlaceholder(graphics, null, artX, artY, artW, artH);
        }

        if (track != null) {
            long now = System.currentTimeMillis();
            float borderPulse = state.isPlaying() ? 0.6f + 0.4f * (float) Math.sin(now / 200.0) : 0.4f;
            int borderA = (int) (0xFF * borderPulse);
            int borderColor = (borderA << 24) | (GuiTheme.ACCENT & 0xFFFFFF);
            GuiRender.drawRoundedBorder(graphics, artX - 1, artY - 1, artW + 2, artH + 2, 4, borderColor);

            if (state.isPlaying()) {
                GuiRender.smoothHoverGlow(graphics, artX - 2, artY - 2, artW + 4, artH + 4, borderPulse * 0.8f);
            }
        }

        int infoX = artX + artW + 10;
        int maxTitleW = controlsX - infoX - 8;
        if (maxTitleW < 40) maxTitleW = 40;
        int textY1 = y + 20;
        int textY2 = textY1 + 18;

        if (track == null) {
            GuiRender.shadowText(graphics, font, "Nothing playing", infoX, textY1, GuiTheme.TEXT_SOFT);
            GuiRender.shadowText(graphics, font, "Backend idle", infoX, textY2, GuiTheme.TEXT_MUTED);
        } else {
            String primary = track.getTitle();
            if (primary == null || primary.isEmpty()) primary = "Unknown Track";

            // Title: show as much as possible, only truncate if truly too long
            if (font.width(primary) > maxTitleW) {
                GuiRender.truncated(graphics, font, primary, infoX, textY1, maxTitleW, GuiTheme.TEXT);
            } else {
                GuiRender.shadowText(graphics, font, primary, infoX, textY1, GuiTheme.TEXT);
            }

            String secondary = track.getArtist();
            if (secondary == null || secondary.isEmpty()) secondary = "Unknown Artist";

            if ("youtube-native".equals(state.getBackendId()) && ServiceManager.getYouTube() != null && (!state.isPlaying() && !state.isPaused())) {
                secondary = ServiceManager.getYouTube().getNativePlaybackMessage();
            }

            if (compact) {
                // Action buttons size in compact mode
                int actionBtnSize = 10;
                int actionBtnPad = 1;
                int actionBtnStep = actionBtnSize + actionBtnPad * 2; // 12
                int actionBtnGap = 2;
                int actionsW = actionBtnStep * 3 + actionBtnGap * 2; // 3 buttons
                int artistTextW = maxTitleW - actionsW - 4;
                if (artistTextW < 20) artistTextW = 20;

                // Artist text (left portion of line 2)
                GuiRender.truncated(graphics, font, secondary, infoX, textY2, artistTextW, GuiTheme.TEXT_MUTED);

                // Heart + Playlist + Download buttons (right portion of line 2 next to artist name)
                int heartBtnX = infoX + artistTextW + 4;
                int heartBtnY = textY2 - 1;
                boolean isFav = LibraryManager.getInstance().isFavorite(track);
                boolean heartHover = GuiRender.inside(mouseX, mouseY, heartBtnX, heartBtnY, actionBtnStep, actionBtnStep);
                GuiRender.mcButton(graphics, heartBtnX, heartBtnY, actionBtnStep, actionBtnStep, heartHover, false);
                if (isFav) {
                    IconRenderer.heartFilled(graphics, font, heartBtnX + actionBtnPad, heartBtnY + actionBtnPad, actionBtnSize, actionBtnSize, GuiTheme.DANGER);
                } else {
                    IconRenderer.heart(graphics, font, heartBtnX + actionBtnPad, heartBtnY + actionBtnPad, actionBtnSize, actionBtnSize, heartHover ? GuiTheme.DANGER : GuiTheme.TEXT_MUTED);
                }

                // Playlist button
                int plBtnX = heartBtnX + actionBtnStep + actionBtnGap;
                boolean plHover = GuiRender.inside(mouseX, mouseY, plBtnX, heartBtnY, actionBtnStep, actionBtnStep);
                GuiRender.mcButton(graphics, plBtnX, heartBtnY, actionBtnStep, actionBtnStep, plHover, showPlaylistPopup);
                if (showPlaylistPopup) {
                    GuiRender.accentGlow(graphics, plBtnX, heartBtnY, actionBtnStep, actionBtnStep);
                    IconRenderer.playlistBook(graphics, font, plBtnX + actionBtnPad, heartBtnY + actionBtnPad, actionBtnSize, actionBtnSize, GuiTheme.ACCENT);
                } else {
                    IconRenderer.playlistBook(graphics, font, plBtnX + actionBtnPad, heartBtnY + actionBtnPad, actionBtnSize, actionBtnSize, plHover ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);
                }

                int dlBtnX = plBtnX + actionBtnStep + actionBtnGap;
                DownloadState dlState = DownloadManager.getInstance().getState(track);
                boolean dlHover = GuiRender.inside(mouseX, mouseY, dlBtnX, heartBtnY, actionBtnStep, actionBtnStep);
                GuiRender.mcButton(graphics, dlBtnX, heartBtnY, actionBtnStep, actionBtnStep, dlHover, dlState == DownloadState.COMPLETED);
                if (dlState == DownloadState.DOWNLOADING) {
                    // Pulsing download icon with glow
                    float pulse = (float)(Math.sin(System.currentTimeMillis() / 300.0) * 0.4 + 0.6);
                    int pulseColor = ((int)(0x60 + 0x60 * pulse) << 24) | (GuiTheme.ACCENT & 0x00FFFFFF);
                    graphics.fill(dlBtnX, heartBtnY, dlBtnX + actionBtnStep, heartBtnY + actionBtnStep, pulseColor);
                    IconRenderer.download(graphics, font, dlBtnX + actionBtnPad, heartBtnY + actionBtnPad, actionBtnSize, actionBtnSize, GuiTheme.ACCENT);
                    // Mini progress bar at bottom of button
                    int barY2 = heartBtnY + actionBtnStep - 2;
                    int barW2 = (int)(actionBtnSize * DownloadManager.getInstance().getProgress(track));
                    graphics.fill(dlBtnX + actionBtnPad, barY2, dlBtnX + actionBtnPad + barW2, barY2 + 1, GuiTheme.ACCENT);
                } else if (dlState == DownloadState.COMPLETED) {
                    IconRenderer.checkmark(graphics, font, dlBtnX + actionBtnPad, heartBtnY + actionBtnPad, actionBtnSize, actionBtnSize, GuiTheme.ACCENT);
                } else {
                    IconRenderer.download(graphics, font, dlBtnX + actionBtnPad, heartBtnY + actionBtnPad, actionBtnSize, actionBtnSize, dlHover ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);
                }
            } else {
                // Action buttons size
                int actionBtnSize = 12;
                int actionBtnPad = 2;
                int actionBtnStep = actionBtnSize + actionBtnPad * 2; // 16
                int actionBtnGap = 3;
                int actionsW = actionBtnStep * 3 + actionBtnGap * 2; // 3 buttons
                int artistTextW = maxTitleW - actionsW - 4;

                // Artist text (left portion of line 2)
                GuiRender.truncated(graphics, font, secondary, infoX, textY2, artistTextW, GuiTheme.TEXT_MUTED);

                // Heart + Playlist + Download buttons (right portion of line 2)
                int heartBtnX = infoX + artistTextW + 4;
                int heartBtnY = textY2 - 2;
                boolean isFav = LibraryManager.getInstance().isFavorite(track);
                boolean heartHover = GuiRender.inside(mouseX, mouseY, heartBtnX, heartBtnY, actionBtnStep, actionBtnStep);
                GuiRender.mcButton(graphics, heartBtnX, heartBtnY, actionBtnStep, actionBtnStep, heartHover, false);
                if (isFav) {
                    IconRenderer.heartFilled(graphics, font, heartBtnX + actionBtnPad, heartBtnY + actionBtnPad, actionBtnSize, actionBtnSize, GuiTheme.DANGER);
                } else {
                    IconRenderer.heart(graphics, font, heartBtnX + actionBtnPad, heartBtnY + actionBtnPad, actionBtnSize, actionBtnSize, heartHover ? GuiTheme.DANGER : GuiTheme.TEXT_MUTED);
                }

                // Playlist button
                int plBtnX = heartBtnX + actionBtnStep + actionBtnGap;
                boolean plHover = GuiRender.inside(mouseX, mouseY, plBtnX, heartBtnY, actionBtnStep, actionBtnStep);
                GuiRender.mcButton(graphics, plBtnX, heartBtnY, actionBtnStep, actionBtnStep, plHover, showPlaylistPopup);
                if (showPlaylistPopup) {
                    GuiRender.accentGlow(graphics, plBtnX, heartBtnY, actionBtnStep, actionBtnStep);
                    IconRenderer.playlistBook(graphics, font, plBtnX + actionBtnPad, heartBtnY + actionBtnPad, actionBtnSize, actionBtnSize, GuiTheme.ACCENT);
                } else {
                    IconRenderer.playlistBook(graphics, font, plBtnX + actionBtnPad, heartBtnY + actionBtnPad, actionBtnSize, actionBtnSize, plHover ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);
                }

                int dlBtnX = plBtnX + actionBtnStep + actionBtnGap;
                DownloadState dlState = DownloadManager.getInstance().getState(track);
                boolean dlHover = GuiRender.inside(mouseX, mouseY, dlBtnX, heartBtnY, actionBtnStep, actionBtnStep);
                GuiRender.mcButton(graphics, dlBtnX, heartBtnY, actionBtnStep, actionBtnStep, dlHover, dlState == DownloadState.COMPLETED);
                if (dlState == DownloadState.DOWNLOADING) {
                    // Pulsing download icon with glow
                    float pulse = (float)(Math.sin(System.currentTimeMillis() / 300.0) * 0.4 + 0.6);
                    int pulseColor = ((int)(0x60 + 0x60 * pulse) << 24) | (GuiTheme.ACCENT & 0x00FFFFFF);
                    graphics.fill(dlBtnX, heartBtnY, dlBtnX + actionBtnStep, heartBtnY + actionBtnStep, pulseColor);
                    IconRenderer.download(graphics, font, dlBtnX + actionBtnPad, heartBtnY + actionBtnPad, actionBtnSize, actionBtnSize, GuiTheme.ACCENT);
                    // Mini progress bar at bottom of button
                    int barY2 = heartBtnY + actionBtnStep - 3;
                    int barW2 = (int)(actionBtnSize * DownloadManager.getInstance().getProgress(track));
                    graphics.fill(dlBtnX + actionBtnPad, barY2, dlBtnX + actionBtnPad + barW2, barY2 + 2, GuiTheme.ACCENT);
                } else if (dlState == DownloadState.COMPLETED) {
                    IconRenderer.checkmark(graphics, font, dlBtnX + actionBtnPad, heartBtnY + actionBtnPad, actionBtnSize, actionBtnSize, GuiTheme.ACCENT);
                } else {
                    IconRenderer.download(graphics, font, dlBtnX + actionBtnPad, heartBtnY + actionBtnPad, actionBtnSize, actionBtnSize, dlHover ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);
                }
            }
        }

        // 6. "Added to playlist" confirmation message
        if (!playlistAddedMsg.isEmpty() && System.currentTimeMillis() - playlistAddedMsgTime < 2000) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(0, 0);
            float fade = 1f - (System.currentTimeMillis() - playlistAddedMsgTime) / 2000f;
            int alpha = (int)(0xFF * fade);
            int msgColor = (alpha << 24) | (GuiTheme.ACCENT & 0x00FFFFFF);
            int msgW = font.width(playlistAddedMsg);
            int msgX = x + 13 + 36 + 10 + (maxTitleW - msgW) / 2;
            GuiRender.shadowText(graphics, font, playlistAddedMsg, msgX, y + 54, msgColor);
            graphics.pose().popMatrix();
        }

        // 7. Waveform / DJ bars at bottom of footer
        renderWaveformBars(graphics, x, y, w, h, state.isPlaying());

        // 8. Playlist popup overlay (drawn last so it's on top)
        if (showPlaylistPopup && track != null) {
            // Push z-offset so popup renders above all other content
            graphics.pose().pushMatrix();
            graphics.pose().translate(0, 0);
            renderPlaylistPopup(graphics, font, x, y, w, h, track, mouseX, mouseY);
            graphics.pose().popMatrix();
        }
    }

    /** Renders smooth, procedurally animated DJ-bar visualizer at the bottom of the footer. */
    private void renderWaveformBars(GuiGraphics graphics, int x, int y, int w, int h, boolean isPlaying) {
        int barW = Math.max(2, (w - BAR_COUNT - 1) / BAR_COUNT);
        int totalBarsW = BAR_COUNT * (barW + 1) - 1;
        int barsX = x + (w - totalBarsW) / 2;
        int barsBaseY = y + h - BAR_MAX_H - 2;

        PlayerState state = PlayerFacade.getInstance().snapshot();
        float amp = PlayerFacade.getInstance().getCurrentAmplitude();

        long now = System.currentTimeMillis();
        float time = (now - START_TIME) / 1000.0f;

        // Calculate target heights with smooth procedural wave when playing/paused
        for (int i = 0; i < BAR_COUNT; i++) {
            float target;
            if (isPlaying) {
                float center = BAR_COUNT / 2f;
                float distFromCenter = Math.abs(i - center) / center; // 0 at center, 1 at edges

                float combined = 0f;
                if (distFromCenter < 0.35f) {
                    // Bass zone (center): large solid beat reactions
                    float speed = 2.0f + amp * 5.0f;
                    float wave = (float) Math.sin(i * 0.3f - time * speed) * 0.3f + 0.7f;
                    combined = wave * (0.4f + 0.6f * amp);
                    // Stronger beat bounce
                    combined += amp * 0.35f * (float) (Math.sin(time * 20.0) * 0.5 + 0.5);
                } else if (distFromCenter < 0.75f) {
                    // Mid zone: smooth flowing waves
                    float speed = 4.0f + amp * 6.0f;
                    float wave = (float) Math.sin(i * 0.45f + time * speed) * 0.4f + 0.4f;
                    combined = wave * (0.5f + 0.5f * amp);
                } else {
                    // Treble zone (edges): fast jittery ripples
                    float speed = 8.0f + amp * 12.0f;
                    float wave = (float) Math.sin(i * 1.1f - time * speed) * 0.25f + 0.25f;
                    float noise = (float) Math.sin(time * 40.0f + i * 5.0f) * 0.15f + 0.15f;
                    combined = (wave * 0.6f + noise * 0.4f) * (0.3f + 0.7f * amp);
                }

                // Global background hum
                combined += 0.04f * (float) Math.sin(time * 3.0f);

                // Shape with an envelope: taller in the middle, tapering to the edges
                float envelope = 1.0f - distFromCenter * 0.55f;
                target = BAR_MIN_H + (BAR_MAX_H - BAR_MIN_H) * combined * envelope;
            } else if (state.isPaused()) {
                float center = BAR_COUNT / 2f;
                float distFromCenter = Math.abs(i - center) / center;
                // Paused: slow breathing ripple that flows outwards from the center
                float wave = (float) Math.sin(distFromCenter * 3.5f - time * 1.8f) * 0.25f + 0.25f;
                float envelope = 1.0f - distFromCenter * 0.35f;
                target = BAR_MIN_H + (BAR_MAX_H - BAR_MIN_H) * wave * envelope;
            } else {
                target = BAR_MIN_H;
            }

            // Lerp current height to target height for organic physics behavior
            float lerpSpeed = isPlaying ? 0.28f : 0.12f;
            barHeights[i] += (target - barHeights[i]) * lerpSpeed;
        }

        // Render bars with a smooth vertical layout and clean premium glow
        for (int i = 0; i < BAR_COUNT; i++) {
            int bh = Math.round(barHeights[i]);
            int bx = barsX + i * (barW + 1);
            int by = barsBaseY + BAR_MAX_H - bh;

            float ratio = (bh - BAR_MIN_H) / (float) (BAR_MAX_H - BAR_MIN_H);

            // Glow halo around each bar when active
            if (isPlaying && ratio > 0.15f) {
                int glowAlpha = (int) (0x1C * ratio);
                int glowColor = (glowAlpha << 24) | (GuiTheme.ACCENT & 0x00FFFFFF);
                graphics.fill(bx - 1, by - 1, bx + barW + 1, by + bh + 1, glowColor);
            }

            // Bar body: gradient from dim at bottom to bright at top
            int bodyAlpha = isPlaying ? (int) (0x5A + 0x6A * ratio) : 0x22;
            int bodyColor = (bodyAlpha << 24) | (GuiTheme.ACCENT & 0x00FFFFFF);
            graphics.fill(bx, by, bx + barW, by + bh, bodyColor);

            // Bright cap at top of bar
            if (bh > BAR_MIN_H && isPlaying) {
                int capAlpha = (int) (0x7F + 0x80 * ratio);
                int capColor = (capAlpha << 24) | (GuiTheme.ACCENT & 0x00FFFFFF);
                graphics.fill(bx, by, bx + barW, by + 1, capColor);
            }
        }
    }

    public boolean mouseClicked(GuiFrame frame, double mouseX, double mouseY) {
        PlayerFacade player = PlayerFacade.getInstance();
        PlayerState state = player.snapshot();

        int x = frame.playerX();
        int y = frame.playerY();
        int w = frame.playerWidth();

        // Center Buttons
        boolean compact = frame.compact();
        int btnW = compact ? 16 : 22;
        int playBtnW = compact ? 20 : 28;
        int btnH = compact ? 16 : 22;
        int hSpacing = compact ? 4 : 10;
        int controlsW = (btnW * 4) + playBtnW + (hSpacing * 4);
        int controlsX = x + w / 2 - controlsW / 2;
        int controlsY = y + (compact ? 37 : 34);

        int bx = controlsX;
        // Button 1: Previous Track (skipBack)
        if (GuiRender.inside(mouseX, mouseY, bx, controlsY, btnW, btnH)) {
            GuiRender.playActionSound();
            player.previous();
            return true;
        }
        bx += btnW + hSpacing;

        // Button 2: Rewind 5s (prev)
        if (GuiRender.inside(mouseX, mouseY, bx, controlsY, btnW, btnH)) {
            GuiRender.playClickSound(1.1f);
            player.seek(Math.max(0, state.getPositionMs() - 5000));
            return true;
        }
        bx += btnW + hSpacing;

        // Button 3: Play/Pause
        if (GuiRender.inside(mouseX, mouseY, bx, controlsY, playBtnW, btnH)) {
            GuiRender.playActionSound();
            player.togglePlayPause();
            return true;
        }
        bx += playBtnW + hSpacing;

        // Button 4: Forward 5s (next)
        if (GuiRender.inside(mouseX, mouseY, bx, controlsY, btnW, btnH)) {
            GuiRender.playClickSound(1.1f);
            player.seek(Math.min(state.getDurationMs(), state.getPositionMs() + 5000));
            return true;
        }
        bx += btnW + hSpacing;

        // Button 5: Next Track (skipForward)
        if (GuiRender.inside(mouseX, mouseY, bx, controlsY, btnW, btnH)) {
            GuiRender.playActionSound();
            player.next();
            return true;
        }

        // Heart + Playlist + Download buttons in track info area (line 2)
        TrackRef track = state.getCurrentTrack();
        if (track != null) {
            int artW = compact ? 32 : 46;
            int artX = x + 10;
            int infoX = artX + artW + 10;
            int actionBtnSize = compact ? 10 : 12;
            int actionBtnPad = compact ? 1 : 2;
            int actionBtnStep = actionBtnSize + actionBtnPad * 2;
            int actionBtnGap = compact ? 2 : 3;
            int actionsW = actionBtnStep * 3 + actionBtnGap * 2;
            int maxTitleW = controlsX - infoX - 8;
            if (maxTitleW < (compact ? 40 : 60)) maxTitleW = compact ? 40 : 60;
            int artistTextW = maxTitleW - actionsW - 4;
            if (compact && artistTextW < 20) artistTextW = 20;
            int textY2 = y + (compact ? 38 : 44);

            int heartBtnX = infoX + artistTextW + 4;
            int heartBtnY = textY2 - (compact ? 1 : 2);

            if (GuiRender.inside(mouseX, mouseY, heartBtnX, heartBtnY, actionBtnStep, actionBtnStep)) {
                GuiRender.playClickSound(1.3f);
                LibraryManager.getInstance().toggleFavorite(track);
                return true;
            }
            // Playlist button
            int plBtnX = heartBtnX + actionBtnStep + actionBtnGap;
            if (GuiRender.inside(mouseX, mouseY, plBtnX, heartBtnY, actionBtnStep, actionBtnStep)) {
                GuiRender.playClickSound(1.1f);
                showPlaylistPopup = !showPlaylistPopup;
                creatingNewPlaylist = false;
                newPlaylistName = "";
                return true;
            }
            int dlBtnX = plBtnX + actionBtnStep + actionBtnGap;
            if (GuiRender.inside(mouseX, mouseY, dlBtnX, heartBtnY, actionBtnStep, actionBtnStep)) {
                GuiRender.playClickSound(1.2f);
                DownloadState dlState = DownloadManager.getInstance().getState(track);
                if (dlState == DownloadState.NONE || dlState == DownloadState.FAILED) {
                    DownloadManager.getInstance().download(track);
                }
                return true;
            }
        }

        // Playlist popup click handling
        if (showPlaylistPopup && track != null) {
            if (clickPlaylistPopup(x, y, w, frame.playerHeight(), track, mouseX, mouseY)) {
                return true;
            }
            // Click outside popup closes it
            showPlaylistPopup = false;
            creatingNewPlaylist = false;
            newPlaylistName = "";
            return true;
        }

        // Progress Bar — only for non-NATIVE tracks (REMOTE/EXTERNAL)
        TrackRef currentTrack = state.getCurrentTrack();
        boolean isNativeTrack = currentTrack != null && currentTrack.getPlaybackType() == PlaybackType.NATIVE;
        if (!isNativeTrack) {
            int progressW = w;
            int progressX = x;
            int progressY = y;
            if (GuiRender.inside(mouseX, mouseY, progressX, progressY, progressW, 12)) {
                GuiRender.playClickSound(0.95f);
                draggingProgress = true;
                updateProgress(player, state, progressX, progressW, mouseX);
                return true;
            }
        }

        // Right Side Controls
        int rightEdge = x + w - 24;
        int volY = y + (compact ? 43 : 48);
        float vol = state.getVolume();
        String pctStr = String.format("%d%%", (int)(vol * 100));

        int pctSlotW = compact ? 22 : 27; // fixed: font.width("100%") ≈ 27
        int pctX = rightEdge - pctSlotW;
        int volW = compact ? 30 : 70;
        int volX = pctX - (compact ? 4 : 8) - volW;
        int volIconSize = compact ? 10 : 14;
        int volIconX = volX - volIconSize - (compact ? 2 : 4);
        int volIconY = volY - (compact ? 7 : 10);

        if (GuiRender.inside(mouseX, mouseY, volIconX - 2, volIconY - 2, volIconSize + 4, volIconSize + 4)) {
            GuiRender.playClickSound(1.05f);
            player.toggleMute();
            return true;
        }

        if (GuiRender.inside(mouseX, mouseY, volX - 2, volY - 6, volW + 4, 12)) {
            GuiRender.playClickSound(1.05f);
            draggingVolume = true;
            updateVolume(player, volX, volW, mouseX);
            return true;
        }

        int btnSize = compact ? 16 : 54;
        int autoplayX = volIconX - btnSize - 6;
        int loopX = autoplayX - btnSize - 6;

        if (compact) {
            if (GuiRender.inside(mouseX, mouseY, loopX, volY - 8, btnSize, btnSize)) {
                GuiRender.playClickSound(1.0f);
                player.cycleLoopMode();
                return true;
            }
            if (GuiRender.inside(mouseX, mouseY, autoplayX, volY - 8, btnSize, btnSize)) {
                GuiRender.playClickSound(1.0f);
                player.toggleAutoplay();
                return true;
            }
        } else {
            int topBtnY = y + 24;
            int topBtnW = 54;
            int topBtnH = 16;
            int badgeAutoplayX = rightEdge - topBtnW;
            int badgeLoopX = badgeAutoplayX - topBtnW - 8;

            if (GuiRender.inside(mouseX, mouseY, badgeLoopX, topBtnY, topBtnW, topBtnH)) {
                GuiRender.playClickSound(1.0f);
                player.cycleLoopMode();
                return true;
            }
            if (GuiRender.inside(mouseX, mouseY, badgeAutoplayX, topBtnY, topBtnW, topBtnH)) {
                GuiRender.playClickSound(1.0f);
                player.toggleAutoplay();
                return true;
            }
        }

        return false;
    }

    public boolean mouseReleased(GuiFrame frame, double mouseX, double mouseY) {
        if (draggingProgress || draggingVolume) {
            if (draggingVolume) {
                ConfigManager.save();
            }
            draggingProgress = false;
            draggingVolume = false;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(GuiFrame frame, double mouseX, double mouseY) {
        PlayerFacade player = PlayerFacade.getInstance();
        boolean compact = frame.compact();
        if (draggingProgress) {
            int progressX = frame.playerX();
            int progressW = frame.playerWidth();
            updateProgress(player, player.snapshot(), progressX, progressW, mouseX);
            return true;
        }
        if (draggingVolume) {
            int rightEdge = frame.playerX() + frame.playerWidth() - 24;
            int volW = compact ? 30 : 70;
            int pctSlotW = compact ? 22 : 27; // fixed: font.width("100%") ≈ 27
            int pctX = rightEdge - pctSlotW;
            int volX = pctX - (compact ? 4 : 8) - volW;
            updateVolume(player, volX, volW, mouseX);
            return true;
        }
        return false;
    }

    private void updateProgress(PlayerFacade player, PlayerState state, int x, int w, double mouseX) {
        long dur = state.getDurationMs();
        if (dur > 0 && dur != Long.MAX_VALUE) {
            double pct = (mouseX - x) / (double) w;
            player.seek((long) (dur * Math.max(0, Math.min(1, pct))));
        }
    }

    private void updateVolume(PlayerFacade player, int x, int w, double mouseX) {
        double pct = (mouseX - x) / (double) w;
        player.setVolume((float) Math.max(0, Math.min(1, pct)), false);
    }

    /** Renders the "Add to Playlist" popup overlay above the footer. */
    private void renderPlaylistPopup(GuiGraphics g, Font f, int barX, int barY, int barW, int barH,
                                      TrackRef track, int mx, int my) {
        LibraryManager lib = LibraryManager.getInstance();
        Set<String> playlistNames = lib.getPlaylistNames();
        List<String> names = new ArrayList<>(playlistNames);

        // Popup dimensions
        int popupW = 170;
        int rowH = 20;
        int headerH = 22;
        int separatorH = 4;
        int createRowH = creatingNewPlaylist ? 38 : rowH;
        int contentH = headerH + separatorH + names.size() * rowH + createRowH + 6;
        int popupH = contentH;

        // Position: centered above the player bar's track info area
        boolean compact = barW < 560;
        int artW = compact ? 32 : 46;
        int infoX = barX + 10 + artW + 10;
        int btnW = compact ? 16 : 22;
        int playBtnW = compact ? 20 : 28;
        int hSpacing = compact ? 4 : 10;
        int controlsW = (btnW * 4) + playBtnW + (hSpacing * 4);
        int controlsX = barX + barW / 2 - controlsW / 2;
        int maxTitleW = controlsX - infoX - 8;
        if (maxTitleW < (compact ? 40 : 60)) maxTitleW = compact ? 40 : 60;
        int infoAreaCenter = infoX + (maxTitleW / 2);
        int popupX = infoAreaCenter - popupW / 2;
        // Clamp so popup stays within screen
        if (popupX < barX + 4) popupX = barX + 4;
        if (popupX + popupW > barX + barW - 4) popupX = barX + barW - popupW - 4;

        int popupY = barY - popupH - 6;
        if (popupY < 2) popupY = barY + barH + 4; // flip below if no room above

        // Dim backdrop behind popup (subtle)
        g.fill(popupX - 2, popupY - 2, popupX + popupW + 2, popupY + popupH + 2, 0x30000000);

        // Scissor clip to popup bounds so nothing bleeds outside
        g.enableScissor(popupX, popupY, popupX + popupW, popupY + popupH);

        // Main panel with raised bevel
        g.fill(popupX, popupY, popupX + popupW, popupY + popupH, GuiTheme.PANEL);
        GuiRender.bevel(g, popupX, popupY, popupW, popupH, false);

        // Accent top border
        g.fill(popupX + 1, popupY, popupX + popupW - 1, popupY + 2, GuiTheme.ACCENT);

        // Header with playlist icon
        IconRenderer.playlistBook(g, f, popupX + 6, popupY + 4, 13, 13, GuiTheme.ACCENT);
        GuiRender.shadowText(g, f, "Add to Playlist", popupX + 22, popupY + 6, GuiTheme.TEXT);

        // Separator line
        int drawY = popupY + headerH;
        g.fill(popupX + 4, drawY, popupX + popupW - 4, drawY + 1, GuiTheme.BEVEL_SHADOW);
        drawY += separatorH;

        // Playlist rows
        for (String name : names) {
            boolean hover = GuiRender.inside(mx, my, popupX + 2, drawY, popupW - 4, rowH);
            if (hover) {
                // Hover highlight with accent tint
                g.fill(popupX + 2, drawY, popupX + popupW - 2, drawY + rowH, GuiTheme.CARD_HOVER);
                GuiRender.bevelHover(g, popupX + 2, drawY, popupW - 4, rowH, false, true);
            }
            int iconColor = hover ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED;
            IconRenderer.playlistBook(g, f, popupX + 8, drawY + 3, 13, 13, iconColor);
            int textColor = hover ? GuiTheme.ACCENT : GuiTheme.TEXT_SOFT;
            GuiRender.truncated(g, f, name, popupX + 24, drawY + 5, popupW - 54, textColor);
            // Track count badge
            int count = lib.getPlaylist(name).size();
            String cntStr = count + "";
            int cntW = fontWidth(f, cntStr) + 6;
            int cntX = popupX + popupW - cntW - 8;
            // Small badge background
            g.fill(cntX, drawY + 4, cntX + cntW, drawY + 15, hover ? 0x30FFFFFF : 0x18FFFFFF);
            GuiRender.text(g, f, cntStr, cntX + 3, drawY + 5, hover ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);
            drawY += rowH;
        }

        // Separator before "Create New"
        g.fill(popupX + 4, drawY, popupX + popupW - 4, drawY + 1, GuiTheme.BEVEL_SHADOW);
        drawY += 3;

        // "Create New Playlist" row
        if (creatingNewPlaylist) {
            boolean hover = GuiRender.inside(mx, my, popupX + 2, drawY, popupW - 4, createRowH);
            if (hover) {
                g.fill(popupX + 2, drawY, popupX + popupW - 2, drawY + createRowH, GuiTheme.CARD_HOVER);
            }
            IconRenderer.plus(g, f, popupX + 8, drawY + 3, 13, 13, GuiTheme.ACCENT);
            // Text input field with focus glow
            int inputX = popupX + 24;
            int inputY = drawY + 2;
            int inputW = popupW - 32;
            int inputH = 16;
            // Focus glow border
            g.fill(inputX - 1, inputY - 1, inputX + inputW + 1, inputY, GuiTheme.GLOW_ACCENT);
            g.fill(inputX - 1, inputY + inputH, inputX + inputW + 1, inputY + inputH + 1, GuiTheme.GLOW_ACCENT);
            g.fill(inputX - 1, inputY, inputX, inputY + inputH, GuiTheme.GLOW_ACCENT);
            g.fill(inputX + inputW, inputY, inputX + inputW + 1, inputY + inputH, GuiTheme.GLOW_ACCENT);
            GuiRender.mcWell(g, inputX, inputY, inputW, inputH);
            // Cursor blink
            String displayText = newPlaylistName;
            if (System.currentTimeMillis() / 500 % 2 == 0) displayText += "_";
            GuiRender.truncated(g, f, displayText, inputX + 3, inputY + 4, inputW - 6, GuiTheme.TEXT);
            // Confirm hint
            GuiRender.text(g, f, "Enter to create \u00B7 Esc to cancel", inputX + 3, drawY + 22, GuiTheme.TEXT_MUTED);
        } else {
            boolean hover = GuiRender.inside(mx, my, popupX + 2, drawY, popupW - 4, rowH);
            if (hover) {
                g.fill(popupX + 2, drawY, popupX + popupW - 2, drawY + rowH, GuiTheme.CARD_HOVER);
                GuiRender.bevelHover(g, popupX + 2, drawY, popupW - 4, rowH, false, true);
            }
            IconRenderer.plus(g, f, popupX + 8, drawY + 3, 13, 13, hover ? GuiTheme.ACCENT : GuiTheme.TEXT_MUTED);
            GuiRender.truncated(g, f, "Create New Playlist", popupX + 24, drawY + 5, popupW - 32, hover ? GuiTheme.ACCENT : GuiTheme.TEXT_SOFT);
        }

        g.disableScissor();
    }

    private int fontWidth(Font f, String s) {
        return f.width(s);
    }

    /** Handle clicks inside the playlist popup. Returns true if a popup item was clicked. */
    private boolean clickPlaylistPopup(int barX, int barY, int barW, int barH, TrackRef track, double mx, double my) {
        LibraryManager lib = LibraryManager.getInstance();
        Set<String> playlistNames = lib.getPlaylistNames();
        List<String> names = new ArrayList<>(playlistNames);

        int popupW = 170;
        int rowH = 20;
        int headerH = 22;
        int separatorH = 4;
        int createRowH = creatingNewPlaylist ? 38 : rowH;
        int popupH = headerH + separatorH + names.size() * rowH + createRowH + 6;

        // Match render positioning
        boolean compact = barW < 560;
        int artW = compact ? 32 : 46;
        int infoX = barX + 10 + artW + 10;
        int btnW = compact ? 16 : 22;
        int playBtnW = compact ? 20 : 28;
        int hSpacing = compact ? 4 : 10;
        int controlsW = (btnW * 4) + playBtnW + (hSpacing * 4);
        int controlsX = barX + barW / 2 - controlsW / 2;
        int maxTitleW = controlsX - infoX - 8;
        if (maxTitleW < (compact ? 40 : 60)) maxTitleW = compact ? 40 : 60;
        int infoAreaCenter = infoX + (maxTitleW / 2);
        int popupX = infoAreaCenter - popupW / 2;
        if (popupX < barX + 4) popupX = barX + 4;
        if (popupX + popupW > barX + barW - 4) popupX = barX + barW - popupW - 4;

        int popupY = barY - popupH - 6;
        if (popupY < 2) popupY = barY + barH + 4;

        // Check if click is inside popup at all
        if (!GuiRender.inside(mx, my, popupX, popupY, popupW, popupH)) return false;

        int drawY = popupY + headerH + separatorH;

        // Playlist rows
        for (String name : names) {
            if (GuiRender.inside(mx, my, popupX + 2, drawY, popupW - 4, rowH)) {
                lib.addToPlaylist(name, track);
                playlistAddedMsg = "Added to " + name;
                playlistAddedMsgTime = System.currentTimeMillis();
                showPlaylistPopup = false;
                creatingNewPlaylist = false;
                newPlaylistName = "";
                return true;
            }
            drawY += rowH;
        }

        // Skip separator
        drawY += 4;

        // "Create New Playlist" row
        if (creatingNewPlaylist) {
            // Click on the input area — no action, just keep focus
            if (GuiRender.inside(mx, my, popupX + 2, drawY, popupW - 4, createRowH)) {
                return true;
            }
        } else {
            if (GuiRender.inside(mx, my, popupX + 2, drawY, popupW - 4, rowH)) {
                creatingNewPlaylist = true;
                newPlaylistName = "";
                return true;
            }
        }

        return true; // consumed click inside popup
    }

    /** Handle key input for the playlist popup (new playlist name). */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (showPlaylistPopup && creatingNewPlaylist) {
            if (keyCode == 256) { // ESC
                creatingNewPlaylist = false;
                newPlaylistName = "";
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // Enter or Numpad Enter
                String name = newPlaylistName.trim();
                if (!name.isEmpty()) {
                    LibraryManager.getInstance().createPlaylist(name);
                    TrackRef track = PlayerFacade.getInstance().snapshot().getCurrentTrack();
                    if (track != null) {
                        LibraryManager.getInstance().addToPlaylist(name, track);
                        playlistAddedMsg = "Created & added to " + name;
                        playlistAddedMsgTime = System.currentTimeMillis();
                    }
                }
                showPlaylistPopup = false;
                creatingNewPlaylist = false;
                newPlaylistName = "";
                return true;
            }
            if (keyCode == 259) { // Backspace
                if (!newPlaylistName.isEmpty()) {
                    newPlaylistName = newPlaylistName.substring(0, newPlaylistName.length() - 1);
                }
                return true;
            }
            return true; // consume all keys while creating
        }
        return false;
    }

    /** Handle character input for the playlist popup. */
    public boolean charTyped(char codePoint, int modifiers) {
        if (showPlaylistPopup && creatingNewPlaylist) {
            if (newPlaylistName.length() < 24 && codePoint >= ' ' && codePoint != '\u007F') {
                newPlaylistName += codePoint;
            }
            return true;
        }
        return false;
    }

    @FunctionalInterface
    private interface IconDraw {
        void draw(GuiGraphics g, Font f, int x, int y, int w, int h, int c);
    }

    private void renderIconButton(GuiGraphics graphics, Font font, int x, int y, int width, int height, boolean active, boolean hovered, IconDraw icon) {
        int iconColor;
        if (active) {
            GuiRender.mcButton(graphics, x, y, width, height, false, true);
            iconColor = GuiTheme.ACCENT;
        } else {
            GuiRender.mcButton(graphics, x, y, width, height, hovered, false);
            iconColor = hovered ? GuiTheme.TEXT : GuiTheme.TEXT_SOFT;
        }
        icon.draw(graphics, font, x, y, width, height, iconColor);
    }

    /** Smooth version of renderIconButton with interpolated hover factor. */
    private void renderIconButtonSmooth(GuiGraphics graphics, Font font, int x, int y, int width, int height, boolean active, float hoverLerp, IconDraw icon) {
        int iconColor;
        if (active) {
            GuiRender.mcButtonSmooth(graphics, x, y, width, height, hoverLerp, true);
            iconColor = GuiTheme.ACCENT;
        } else {
            GuiRender.mcButtonSmooth(graphics, x, y, width, height, hoverLerp, false);
            // Icon color interpolates: TEXT_SOFT → ACCENT_BRIGHT on hover
            iconColor = AnimationHelper.lerpColor(GuiTheme.TEXT_SOFT, GuiTheme.ACCENT_BRIGHT, hoverLerp);
        }
        icon.draw(graphics, font, x, y, width, height, iconColor);
    }

    private void renderIconBadge(GuiGraphics graphics, Font font, String label, int x, int y, int width, int height, boolean active, boolean hovered, IconDraw icon) {
        float badgeLerp = HoverTracker.tick("pb_badge_" + label, hovered);
        GuiRender.mcButtonSmooth(graphics, x, y, width, height, badgeLerp, active);

        int iconColor;
        int textColor;
        if (active) {
            // Premium glow instead of blue strip border
            GuiRender.accentGlow(graphics, x, y, width, height);
            iconColor = GuiTheme.ACCENT;
            textColor = GuiTheme.TEXT;
        } else {
            iconColor = AnimationHelper.lerpColor(GuiTheme.TEXT_MUTED, GuiTheme.TEXT, badgeLerp);
            textColor = AnimationHelper.lerpColor(GuiTheme.TEXT_MUTED, GuiTheme.TEXT, badgeLerp);
        }

        // Draw icon: smaller (10px), vertically centered on left side
        int iconSize = 10;
        int iconX = x + 4;
        int iconY = y + (height - iconSize) / 2;
        icon.draw(graphics, font, iconX, iconY, iconSize, iconSize, iconColor);
        // Draw label text after icon, vertically centered
        GuiRender.shadowText(graphics, font, label, iconX + iconSize + 3, y + (height / 2) - 4, textColor);
    }

    private String formatDuration(long ms) {
        if (ms <= 0 || ms == Long.MAX_VALUE) return "0:00";
        long totalSeconds = ms / 1000;
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}

