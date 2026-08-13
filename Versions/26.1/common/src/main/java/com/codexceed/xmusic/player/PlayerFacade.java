package com.codexceed.xmusic.player;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.audio.AudioPlayer;
import com.codexceed.xmusic.audio.PlaybackMode;
import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.config.XMusicConfig;
import com.codexceed.xmusic.lavaplayer.LavaPlayerBackend;
import com.codexceed.xmusic.player.backend.NativeAudioBackend;
import com.codexceed.xmusic.player.backend.PlaybackBackend;
import com.codexceed.xmusic.service.ServiceManager;
import com.codexceed.xmusic.source.PlaybackType;
import com.codexceed.xmusic.source.TrackRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Central routing facade for all playback operations.
 *
 * <h3>Design: Source-Agnostic Active List</h3>
 * All navigation operates on the generic {@code queue} + {@code currentIndex}.
 * The queue can be populated from <em>any</em> source — search results,
 * library, playlists, mixes. Controls never know or care about the source.
 *
 * <h3>Two Navigation Axes</h3>
 * <ul>
 *   <li><b>History ({@code < >})</b>: Navigates through previously played
 *       tracks, regardless of which list they came from.</li>
 *   <li><b>List ({@code |< >|})</b>: Navigates within the current active
 *       list (queue), respecting the current index.</li>
 * </ul>
 *
 * <h3>Loop Modes (per-track)</h3>
 * Independent of {@link PlaybackMode}. When a track ends:
 * <ul>
 *   <li>{@code loopCount = 0}: advance normally</li>
 *   <li>{@code loopCount = 3/5}: replay up to N times, then advance</li>
 *   <li>{@code loopCount = -1}: repeat forever</li>
 * </ul>
 */
public final class PlayerFacade {
    private static class Holder {
        static final PlayerFacade INSTANCE = new PlayerFacade();
    }
    private final List<PlaybackBackend> backends = new CopyOnWriteArrayList<>();
    private final List<TrackRef> queue = new ArrayList<>();
    private final PlaybackBackend nativeBackend = new NativeAudioBackend();
    private final LavaPlayerBackend lavaBackend;

    private PlaybackBackend activeBackend;
    private int currentIndex = -1;
    private String playbackContext = "none";
    private String lastError = null;

    public void setPlaybackContext(String context) {
        this.playbackContext = context;
        XMusic.LOGGER.info("[Player] Playback context set to: " + context);
    }

    public String getPlaybackContext() {
        return playbackContext;
    }

    public String getLastError() { return lastError; }
    public void setLastError(String error) { 
        this.lastError = formatUserFriendlyError(error); 
    }

    public static String formatUserFriendlyError(String raw) {
        if (raw == null || raw.isBlank()) return "Unknown error occurred";
        String lower = raw.toLowerCase();
        if (lower.contains("429") || lower.contains("rate limit") || lower.contains("too many requests")) {
            return "YouTube Rate Limited (Add po_token in xmusic.json)";
        } else if (lower.contains("sign in to confirm your age") || lower.contains("age restricted")) {
            return "Age Restricted Video (Requires account)";
        } else if (lower.contains("video requires login") || lower.contains("please sign in") || lower.contains("bot")) {
            return "YouTube Blocked Request (Add po_token in xmusic.json)";
        } else if (lower.contains("unknownhost") || lower.contains("network is unreachable") || lower.contains("connection reset") || lower.contains("timed out")) {
            return "No Internet Connection";
        } else if (lower.contains("unavailable") || lower.contains("private video") || lower.contains("not available")) {
            return "Video Unavailable or Private";
        } else if (lower.contains("copyright")) {
            return "Blocked due to Copyright";
        } else if (lower.contains("no matching track")) {
            return "No matching track found";
        }
        int colonIdx = raw.lastIndexOf(':');
        if (colonIdx >= 0 && colonIdx < raw.length() - 1 && raw.substring(0, colonIdx).contains("Exception")) {
            return raw.substring(colonIdx + 1).trim();
        }
        return raw;
    }
    public void clearLastError() { this.lastError = null; }

    // ── History (for < > controls) ─────────────────────────────────────────
    /** Chronological list of all played tracks (most recent last). */
    private final List<TrackRef> playHistory = new ArrayList<>();
    /** Current position in the history list. -1 = at the head (latest). */
    private int historyIndex = -1;
    private static final int MAX_HISTORY = 200;

    // ── Loop (per-track repeat) ────────────────────────────────────────────
    /** 0=off, 3=repeat 3×, 5=repeat 5×, -1=infinite */
    private int loopCount = 0;
    /** How many times the current track has been played in this loop cycle */
    private int currentLoopIteration = 0;
    /** Cycle order: off → 3 → 5 → ∞ → off */
    private static final int[] LOOP_CYCLE = {0, 3, 5, -1};

    private PlayerFacade() {
        
        lavaBackend = ServiceManager.getLavaPlayerBackend();
        if (lavaBackend != null) {
            lavaBackend.setFacade(this);
            backends.add(lavaBackend);
        } else {
            XMusic.LOGGER.warn("[Facade] LavaPlayerBackend not available -- ServiceManager may not be initialized yet");
        }
        backends.add(nativeBackend);
        activeBackend = nativeBackend;
    }

    public static PlayerFacade getInstance() {
        return Holder.INSTANCE;
    }

    public void registerBackend(PlaybackBackend backend) {
        if (backend == null || backends.contains(backend)) return;
        backends.add(backend);
    }

    // ─── Core Play ─────────────────────────────────────────────────────────

    public boolean play(TrackRef track) {
        if (track == null) return false;
        clearLastError();

        PlaybackBackend backend = resolveBackend(track);
        if (backend == null) {
            XMusic.LOGGER.warn("No playback backend available for source {} and playback type {}",
                    track.getSourceId(), track.getPlaybackType());
            return false;
        }

        // Stop ALL other backends to prevent overlapping audio
        for (PlaybackBackend b : backends) {
            if (b != backend) {
                b.stop();
            }
        }

        activeBackend = backend;
        backend.setVolume(ConfigManager.get().volume);
        boolean played = backend.play(track);
        if (!played && activeBackend == backend) {
            activeBackend = nativeBackend;
        }

        // Push to play history (only if not navigating history)
        if (played) {
            pushToHistory(track);
            com.codexceed.xmusic.library.LibraryManager.getInstance().recordPlay(track);
            currentLoopIteration = 0; // reset loop counter for new track
        }

        return played;
    }

    public boolean playQueue(List<TrackRef> tracks, int startIndex) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof com.codexceed.xmusic.gui.screen.XMusicScreen) {
            com.codexceed.xmusic.gui.GuiRoute route = ((com.codexceed.xmusic.gui.screen.XMusicScreen) mc.screen).getActiveRoute();
            if (route == com.codexceed.xmusic.gui.GuiRoute.HOME) {
                setPlaybackContext("home");
            } else if (route == com.codexceed.xmusic.gui.GuiRoute.SEARCH) {
                setPlaybackContext("search");
            } else if (route == com.codexceed.xmusic.gui.GuiRoute.LIBRARY) {
                setPlaybackContext("library");
            } else if (route == com.codexceed.xmusic.gui.GuiRoute.DOWNLOADS) {
                setPlaybackContext("downloads");
            }
        }

        clearLastError();
        queue.clear();
        if (tracks != null) {
            queue.addAll(tracks);
        }

        if (queue.isEmpty()) {
            currentIndex = -1;
            return false;
        }

        currentIndex = Math.max(0, Math.min(startIndex, queue.size() - 1));

        boolean isNative = isNativeQueue(queue);
        XMusic.LOGGER.info("[Facade] playQueue: {} tracks, index={}, native={}, first={}",
                queue.size(), currentIndex, isNative, queue.get(0).getDisplayName());
        if (isNative) {
            XMusic.LOGGER.info("[Facade] nativeUri of first track: '{}'", queue.get(0).getNativeUri());
        }

        if (isNative) {
            // Filter out tracks with empty nativeUri — they can't be played
            List<TrackRef> playable = new ArrayList<>();
            for (TrackRef t : queue) {
                String uri = t.getNativeUri();
                if (uri != null && !uri.isEmpty()) {
                    playable.add(t);
                } else {
                    XMusic.LOGGER.warn("[Facade] Skipping track '{}' — nativeUri is empty", t.getDisplayName());
                }
            }
            if (playable.isEmpty()) {
                XMusic.LOGGER.error("[Facade] No playable tracks in native queue — all have empty nativeUri");
                currentIndex = -1;
                return false;
            }
            // Adjust currentIndex if tracks were removed before it
            int adjustedIndex = Math.max(0, Math.min(currentIndex, playable.size() - 1));

            // Stop ALL non-native backends to prevent overlapping audio
            for (PlaybackBackend b : backends) {
                if (b != nativeBackend) {
                    b.stop();
                }
            }
            activeBackend = nativeBackend;
            AudioPlayer.getInstance().playQueue(toAudioTracks(playable), adjustedIndex);
            return true;
        }

        return play(queue.get(currentIndex));
    }

    // ─── Transport Controls ────────────────────────────────────────────────

    public void pause() {
        activeBackend.pause();
    }

    public void resume() {
        activeBackend.resume();
    }

    public void togglePlayPause() {
        PlayerState state = snapshot();
        if (state.isPlaying()) {
            pause();
            return;
        }
        if (state.isPaused()) {
            resume();
            return;
        }
        if (currentIndex >= 0 && currentIndex < queue.size()) {
            play(queue.get(currentIndex));
        }
    }

    public void stop() {
        activeBackend.stop();
    }

    public void seek(long positionMs) {
        activeBackend.seek(positionMs);
    }

    // ─── List Navigation (|< >| controls) ──────────────────────────────────
    // Moves within the current active list (queue), regardless of history.

    /**
     * Play the next track in the active list.
     * Respects loop count: if the current track should loop, it replays instead.
     */
    public void next() {
        // Handle loop: replay current track if loop count not exhausted
        if (shouldLoopCurrentTrack()) {
            replayCurrentTrack();
            return;
        }

        if (queue.isEmpty()) {
            if (activeBackend == nativeBackend) {
                AudioPlayer.getInstance().next();
                currentIndex = AudioPlayer.getInstance().getCurrentIndex();
            }
            return;
        }

        if (isNativeQueue(queue)) {
            AudioPlayer.getInstance().next();
            currentIndex = AudioPlayer.getInstance().getCurrentIndex();
            activeBackend = nativeBackend;
            return;
        }

        int nextIndex = resolveNextIndex();
        if (nextIndex < 0) {
            stop();
            return;
        }

        currentIndex = nextIndex;
        currentLoopIteration = 0;
        play(queue.get(currentIndex));
    }

    /**
     * Play the previous track in the active list.
     */
    public void previous() {
        if (queue.isEmpty()) {
            if (activeBackend == nativeBackend) {
                AudioPlayer.getInstance().previous();
                currentIndex = AudioPlayer.getInstance().getCurrentIndex();
            }
            return;
        }

        if (isNativeQueue(queue)) {
            AudioPlayer.getInstance().previous();
            currentIndex = AudioPlayer.getInstance().getCurrentIndex();
            activeBackend = nativeBackend;
            return;
        }

        int previousIndex = resolvePreviousIndex();
        if (previousIndex < 0) {
            return;
        }

        currentIndex = previousIndex;
        currentLoopIteration = 0;
        play(queue.get(currentIndex));
    }

    /** Explicit list-forward: same as next() but always advances (ignores loop). */
    public void listNext() {
        if (queue.isEmpty()) return;
        int nextIndex = currentIndex + 1;
        if (nextIndex >= queue.size()) nextIndex = 0; // wrap
        currentIndex = nextIndex;
        currentLoopIteration = 0;
        play(queue.get(currentIndex));
    }

    /** Explicit list-backward: same as previous() but always goes back. */
    public void listPrevious() {
        if (queue.isEmpty()) return;
        int prevIndex = currentIndex - 1;
        if (prevIndex < 0) prevIndex = queue.size() - 1; // wrap
        currentIndex = prevIndex;
        currentLoopIteration = 0;
        play(queue.get(currentIndex));
    }

    // ─── History Navigation (< > controls) ─────────────────────────────────
    // Navigates through chronologically played tracks, across all lists.

    /**
     * Go back to the previously played track (like browser back button).
     */
    public void historyBack() {
        if (!canHistoryBack()) return;

        // If we're at the head, start from the last entry
        if (historyIndex < 0) {
            historyIndex = playHistory.size() - 2; // -1 is current, -2 is previous
        } else {
            historyIndex--;
        }

        if (historyIndex >= 0 && historyIndex < playHistory.size()) {
            TrackRef track = playHistory.get(historyIndex);
            // Find this track in the current queue to update currentIndex
            int queueIdx = findInQueue(track);
            if (queueIdx >= 0) currentIndex = queueIdx;

            // Play without pushing to history (we're navigating, not creating new history)
            playWithoutHistory(track);
        }
    }

    /**
     * Go forward in history (after going back).
     */
    public void historyForward() {
        if (!canHistoryForward()) return;

        historyIndex++;
        if (historyIndex >= 0 && historyIndex < playHistory.size()) {
            TrackRef track = playHistory.get(historyIndex);
            int queueIdx = findInQueue(track);
            if (queueIdx >= 0) currentIndex = queueIdx;
            playWithoutHistory(track);
        }

        // If we've reached the head, reset historyIndex
        if (historyIndex >= playHistory.size() - 1) {
            historyIndex = -1;
        }
    }

    public boolean canHistoryBack() {
        if (playHistory.size() < 2) return false;
        if (historyIndex < 0) return true; // at head, can go back
        return historyIndex > 0;
    }

    public boolean canHistoryForward() {
        if (historyIndex < 0) return false; // already at head
        return historyIndex < playHistory.size() - 1;
    }

    // ─── Loop Control ──────────────────────────────────────────────────────

    /** Cycle: Off → ×3 → ×5 → ∞ → Off. Disables autoplay when loop is active. */
    public void cycleLoopMode() {
        int currentIdx = 0;
        for (int i = 0; i < LOOP_CYCLE.length; i++) {
            if (LOOP_CYCLE[i] == loopCount) {
                currentIdx = i;
                break;
            }
        }
        loopCount = LOOP_CYCLE[(currentIdx + 1) % LOOP_CYCLE.length];
        currentLoopIteration = 0;

        // Loop and autoplay are mutually exclusive
        if (loopCount != 0) {
            autoplay = false;
        } else {
            autoplay = true;
        }

        XMusic.LOGGER.info("[Player] Loop mode: {} (count={}), autoplay={}", getLoopDisplay(), loopCount, autoplay);
    }

    public int getLoopCount() { return loopCount; }
    public int getLoopIteration() { return currentLoopIteration; }

    public String getLoopDisplay() {
        if (loopCount == 0) return "\u2014";     // — (off)
        if (loopCount == -1) return "\u221E";    // ∞
        return "\u00D7" + loopCount;              // ×3, ×5
    }

    public boolean shouldLoopCurrentTrack() {
        if (loopCount == 0) return false;
        if (loopCount == -1) return true; // infinite
        return currentLoopIteration < loopCount - 1; // -1 because first play counts
    }

    private void replayCurrentTrack() {
        currentLoopIteration++;
        if (currentIndex >= 0 && currentIndex < queue.size()) {
            playWithoutHistory(queue.get(currentIndex));
        }
    }

    /**
     * Called by backends (e.g. NativeAudioBackend) when a track ends and loop is active.
     * Increments loop iteration and replays the current track without pushing to history.
     */
    public void replayCurrentTrackFromBackend() {
        currentLoopIteration++;
        if (currentIndex >= 0 && currentIndex < queue.size()) {
            playWithoutHistory(queue.get(currentIndex));
        } else {
            // No queue — replay via active backend directly
            PlayerState state = snapshot();
            TrackRef current = state.getCurrentTrack();
            if (current != null) {
                playWithoutHistory(current);
            }
        }
    }

    // ─── Volume ────────────────────────────────────────────────────────────

    public void setVolume(float volume) {
        setVolume(volume, true);
    }

    public void setVolume(float volume, boolean saveToDisk) {
        float clamped = Math.max(0f, Math.min(1f, volume));
        if (clamped > 0f) {
            ConfigManager.get().lastNonZeroVolume = clamped;
        }
        ConfigManager.get().volume = clamped;
        if (saveToDisk) {
            ConfigManager.save();
        }
        activeBackend.setVolume(clamped);
    }

    public void adjustVolume(float delta) {
        setVolume(snapshot().getVolume() + delta);
    }

    public void toggleMute() {
        float current = snapshot().getVolume();
        if (current > 0f) {
            ConfigManager.get().lastNonZeroVolume = current;
            setVolume(0f);
            return;
        }
        float restore = ConfigManager.get().lastNonZeroVolume;
        setVolume(restore > 0f ? restore : 0.8f);
    }

    // ─── Playback Mode & Autoplay ─────────────────────────────────────────

    private boolean autoplay = true;

    public PlaybackMode getPlaybackMode() {
        return AudioPlayer.getInstance().getPlaybackMode();
    }

    public void setPlaybackMode(PlaybackMode mode) {
        AudioPlayer.getInstance().setPlaybackMode(mode);
    }

    public void cyclePlaybackMode() {
        AudioPlayer.getInstance().cyclePlaybackMode();
    }

    public boolean isAutoplay() {
        return autoplay;
    }

    public void toggleAutoplay() {
        this.autoplay = !this.autoplay;
        // Loop and autoplay are mutually exclusive
        if (autoplay) {
            loopCount = 0;
            currentLoopIteration = 0;
        }
    }

    // ─── Tick & Snapshot ───────────────────────────────────────────────────

    public void tick() {
        activeBackend.tick();
    }

    public PlayerState snapshot() {
        PlayerState base = activeBackend != null ? activeBackend.snapshot() : PlayerState.idle();

        // Overlay facade-level state (loop, history) onto the backend's snapshot
        return new PlayerState(
                base.getBackendId(),
                base.getCurrentTrack(),
                base.isPlaying(),
                base.isPaused(),
                base.getPositionMs(),
                base.getDurationMs(),
                base.getVolume(),
                base.getPlaybackMode(),
                currentIndex,
                queue.size(),
                loopCount,
                currentLoopIteration,
                canHistoryBack(),
                canHistoryForward(),
                autoplay
        );
    }

    // ─── Getters ───────────────────────────────────────────────────────────

    /**
     * Add a track to the queue right after the current position.
     * Does not interrupt current playback.
     */
    public void addToQueue(TrackRef track) {
        if (track == null) return;
        int insertPos = currentIndex + 1;
        if (insertPos < 0 || insertPos > queue.size()) insertPos = queue.size();
        queue.add(insertPos, track);
    }

    /**
     * Add multiple tracks to the queue after the current position.
     */
    public void addToQueue(List<TrackRef> tracks) {
        if (tracks == null || tracks.isEmpty()) return;
        int insertPos = currentIndex + 1;
        if (insertPos < 0 || insertPos > queue.size()) insertPos = queue.size();
        queue.addAll(insertPos, tracks);
    }

    public List<TrackRef> getQueue() {
        return Collections.unmodifiableList(queue);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    /**
     * Get the play history (most recent last).
     */
    public List<TrackRef> getPlayHistory() {
        return Collections.unmodifiableList(playHistory);
    }

    // ─── Internal Helpers ──────────────────────────────────────────────────

    private void pushToHistory(TrackRef track) {
        if (historyIndex >= 0) {
            // If we navigated back in history, truncate forward history
            while (playHistory.size() > historyIndex + 1) {
                playHistory.remove(playHistory.size() - 1);
            }
            historyIndex = -1; // back to head
        }

        // Don't push duplicates of the same track consecutively
        if (!playHistory.isEmpty()) {
            TrackRef last = playHistory.get(playHistory.size() - 1);
            if (last.getId().equals(track.getId()) && last.getSourceId().equals(track.getSourceId())) {
                return;
            }
        }

        playHistory.add(track);

        // Cap history size
        while (playHistory.size() > MAX_HISTORY) {
            playHistory.remove(0);
        }
    }

    /**
     * Play a track without pushing to history (used by history navigation
     * and loop replay to avoid corrupting the history stack).
     */
    private boolean playWithoutHistory(TrackRef track) {
        if (track == null) return false;

        PlaybackBackend backend = resolveBackend(track);
        if (backend == null) return false;

        // Stop ALL other backends to prevent overlapping audio
        for (PlaybackBackend b : backends) {
            if (b != backend) {
                b.stop();
            }
        }

        activeBackend = backend;
        backend.setVolume(ConfigManager.get().volume);
        return backend.play(track);
    }

    private int findInQueue(TrackRef track) {
        for (int i = 0; i < queue.size(); i++) {
            TrackRef q = queue.get(i);
            if (q.getId().equals(track.getId()) && q.getSourceId().equals(track.getSourceId())) {
                return i;
            }
        }
        return -1;
    }

    private int resolveNextIndex() {
        if (queue.isEmpty()) return -1;

        PlaybackMode mode = getPlaybackMode();
        if (mode == PlaybackMode.SHUFFLE && queue.size() > 1) {
            return randomIndexExcluding(currentIndex);
        }

        int nextIndex = currentIndex + 1;
        if (nextIndex >= queue.size()) {
            if (mode == PlaybackMode.REPEAT_ALL || mode == PlaybackMode.SHUFFLE) {
                return 0;
            }
            return -1;
        }
        return nextIndex;
    }

    private int resolvePreviousIndex() {
        if (queue.isEmpty()) return -1;

        PlaybackMode mode = getPlaybackMode();
        if (mode == PlaybackMode.SHUFFLE && queue.size() > 1) {
            return randomIndexExcluding(currentIndex);
        }

        int previousIndex = currentIndex - 1;
        if (previousIndex < 0) {
            return mode == PlaybackMode.REPEAT_ALL ? queue.size() - 1 : 0;
        }
        return previousIndex;
    }

    private int randomIndexExcluding(int excludedIndex) {
        if (queue.size() <= 1) return 0;
        int candidate = excludedIndex;
        while (candidate == excludedIndex) {
            candidate = ThreadLocalRandom.current().nextInt(queue.size());
        }
        return candidate;
    }

    private PlaybackBackend resolveBackend(TrackRef track) {
        for (PlaybackBackend backend : backends) {
            if (backend.supports(track)) {
                return backend;
            }
        }
        return null;
    }

    private boolean isNativeQueue(List<TrackRef> tracks) {
        for (TrackRef track : tracks) {
            if (track.getPlaybackType() != PlaybackType.NATIVE) {
                return false;
            }
        }
        return true;
    }

    private List<com.codexceed.xmusic.audio.AudioTrack> toAudioTracks(List<TrackRef> tracks) {
        List<com.codexceed.xmusic.audio.AudioTrack> mapped = new ArrayList<>(tracks.size());
        for (TrackRef track : tracks) {
            mapped.add(TrackRefMapper.toAudioTrack(track));
        }
        return mapped;
    }

    // ─── Auto-Resume ──────────────────────────────────────────────────────

    /** Save current playback state to config for auto-resume on next launch. */
    public void saveResumeState() {
        XMusicConfig cfg = ConfigManager.get();
        PlayerState state = snapshot();
        TrackRef track = state.getCurrentTrack();

        boolean isResolving = false;
        if (activeBackend instanceof com.codexceed.xmusic.lavaplayer.LavaPlayerBackend) {
            isResolving = ((com.codexceed.xmusic.lavaplayer.LavaPlayerBackend) activeBackend).isResolving();
        }
        if (isResolving) {
            return;
        }

        if (track == null || (!state.isPlaying() && !state.isPaused())) {
            cfg.resumeTrackId = "";
            cfg.resumeWasPlaying = false;
            ConfigManager.save();
            return;
        }
        cfg.resumeTrackId = track.getId() != null ? track.getId() : "";
        cfg.resumeTrackArtworkUrl = track.getArtworkUrl() != null ? track.getArtworkUrl() : "";
        cfg.resumeSourceId = track.getSourceId() != null ? track.getSourceId() : "";
        cfg.resumeTrackTitle = track.getTitle() != null ? track.getTitle() : "";
        cfg.resumeTrackArtist = track.getArtist() != null ? track.getArtist() : "";
        cfg.resumeTrackNativeUri = track.getNativeUri() != null ? track.getNativeUri() : "";
        cfg.resumeTrackRemoteUri = track.getRemoteUri() != null ? track.getRemoteUri() : "";
        cfg.resumeTrackExternalUrl = track.getExternalUrl() != null ? track.getExternalUrl() : "";
        cfg.resumeTrackPlaybackType = track.getPlaybackType() != null ? track.getPlaybackType().name() : "";
        cfg.resumePositionMs = state.getPositionMs();
        cfg.resumeWasPlaying = state.isPlaying();
        ConfigManager.save();
    }

    /** Attempt to restore the last playing track from saved resume state. */
    public void restoreResumeState() {
        XMusicConfig cfg = ConfigManager.get();
        if (!cfg.autoResume || cfg.resumeTrackId.isEmpty()) return;

        TrackRef.Builder builder = new TrackRef.Builder()
                .id(cfg.resumeTrackId)
                .sourceId(cfg.resumeSourceId)
                .title(cfg.resumeTrackTitle)
                .artist(cfg.resumeTrackArtist)
                .nativeUri(cfg.resumeTrackNativeUri)
                .remoteUri(cfg.resumeTrackRemoteUri)
                .externalUrl(cfg.resumeTrackExternalUrl)
                .artworkUrl(cfg.resumeTrackArtworkUrl);

        try {
            builder.playbackType(PlaybackType.valueOf(cfg.resumeTrackPlaybackType));
        } catch (Exception e) {
            builder.playbackType(PlaybackType.NATIVE);
        }

        TrackRef track = builder.build();
        boolean played = play(track);
        if (played && cfg.resumePositionMs > 0) {
            seek(cfg.resumePositionMs);
        }
        if (played) {
            pause(); // Always start paused on launch
        }
        XMusic.LOGGER.info("[AutoResume] Restored track '{}' (pos={}ms, forcePaused=true)",
                cfg.resumeTrackTitle, cfg.resumePositionMs);
    }

    public void getWaveform(float[] dest) {
        if (activeBackend == nativeBackend) {
            ((com.codexceed.xmusic.player.backend.NativeAudioBackend) nativeBackend).getWaveform(dest);
        } else if (activeBackend == lavaBackend) {
            ((com.codexceed.xmusic.lavaplayer.LavaPlayerBackend) lavaBackend).getWaveform(dest);
        } else {
            java.util.Arrays.fill(dest, 0f);
        }
    }

    public float getCurrentAmplitude() {
        if (activeBackend == nativeBackend) {
            return ((com.codexceed.xmusic.player.backend.NativeAudioBackend) nativeBackend).getCurrentAmplitude();
        } else if (activeBackend == lavaBackend) {
            return ((com.codexceed.xmusic.lavaplayer.LavaPlayerBackend) lavaBackend).getCurrentAmplitude();
        }
        return 0f;
    }
}
