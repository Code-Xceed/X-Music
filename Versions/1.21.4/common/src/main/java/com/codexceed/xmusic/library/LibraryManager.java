package com.codexceed.xmusic.library;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.source.TrackRef;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages the user's music library, including favorites, custom playlists, and downloaded tracks.
 */
public final class LibraryManager {
    private static final String LIBRARY_FILE_NAME = "library.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static LibraryManager instance;
    private LibraryData data;
    private Path libraryPath;

    private LibraryManager() {}

    public static synchronized LibraryManager getInstance() {
        if (instance == null) {
            instance = new LibraryManager();
        }
        return instance;
    }

    public void init(Path configDir) {
        this.libraryPath = configDir.resolve(LIBRARY_FILE_NAME);
        load();
    }

    private void load() {
        if (libraryPath == null) return;
        File file = libraryPath.toFile();
        if (file.exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                data = GSON.fromJson(reader, LibraryData.class);
                if (data == null) data = new LibraryData();
            } catch (Exception e) {
                XMusic.LOGGER.error("Failed to load library, creating new.", e);
                data = new LibraryData();
            }
        } else {
            data = new LibraryData();
            save();
        }
    }

    public void save() {
        if (libraryPath == null) return;
        try {
            Files.createDirectories(libraryPath.getParent());
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(libraryPath.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            XMusic.LOGGER.error("Failed to save library!", e);
        }
    }

    public boolean toggleFavorite(TrackRef track) {
        // Find existing favorite by ID (cross-source: youtube vs local)
        TrackRef existing = findFavoriteById(track.getId());
        if (existing != null) {
            data.favorites.remove(existing);
            save();
            return false;
        }
        data.favorites.add(track);
        save();
        return true;
    }

    public boolean isFavorite(TrackRef track) {
        // Match by ID across sources so downloaded tracks inherit favorites
        return findFavoriteById(track.getId()) != null;
    }

    /** Find a favorite track by ID, matching across different sourceIds. */
    private TrackRef findFavoriteById(String id) {
        for (TrackRef t : data.favorites) {
            if (t.getId().equals(id)) return t;
        }
        return null;
    }

    public Set<TrackRef> getFavorites() {
        return Collections.unmodifiableSet(data.favorites);
    }

    public void markAsDownloaded(TrackRef track) {
        data.downloaded.add(track);
        save();
    }

    public void removeDownloaded(TrackRef track) {
        data.downloaded.remove(track);
        save();
    }

    public boolean isDownloaded(TrackRef track) {
        return data.downloaded.contains(track);
    }

    public Set<TrackRef> getDownloadedTracks() {
        return Collections.unmodifiableSet(data.downloaded);
    }

    // ─── Playlist CRUD ──────────────────────────────────────────────────────

    public List<TrackRef> getPlaylist(String name) {
        return Collections.unmodifiableList(data.playlists.getOrDefault(name, Collections.emptyList()));
    }

    public void addToPlaylist(String name, TrackRef track) {
        data.playlists.computeIfAbsent(name, k -> new ArrayList<>()).add(track);
        save();
    }

    public Set<String> getPlaylistNames() {
        return Collections.unmodifiableSet(data.playlists.keySet());
    }

    public void createPlaylist(String name) {
        if (name == null || name.isBlank()) return;
        data.playlists.putIfAbsent(name.trim(), new ArrayList<>());
        save();
    }

    public void renamePlaylist(String oldName, String newName) {
        if (oldName == null || newName == null || newName.isBlank()) return;
        List<TrackRef> tracks = data.playlists.remove(oldName);
        if (tracks != null) {
            data.playlists.put(newName.trim(), tracks);
            save();
        }
    }

    public void deletePlaylist(String name) {
        if (data.playlists.remove(name) != null) {
            save();
        }
    }

    public void removeFromPlaylist(String name, TrackRef track) {
        List<TrackRef> list = data.playlists.get(name);
        if (list != null && list.remove(track)) {
            save();
        }
    }

    public void removeFromPlaylist(String name, int index) {
        List<TrackRef> list = data.playlists.get(name);
        if (list != null && index >= 0 && index < list.size()) {
            list.remove(index);
            save();
        }
    }

    public void moveInPlaylist(String name, int fromIdx, int toIdx) {
        List<TrackRef> list = data.playlists.get(name);
        if (list == null || fromIdx < 0 || fromIdx >= list.size()
                || toIdx < 0 || toIdx >= list.size()) return;
        TrackRef moved = list.remove(fromIdx);
        list.add(toIdx, moved);
        save();
    }

    // ─── Auto-Grouping ────────────────────────────────────────────────────

    /**
     * Union of all tracks across favorites and all playlists, deduped by (id, sourceId).
     */
    public List<TrackRef> getAllTracks() {
        Map<String, TrackRef> unique = new LinkedHashMap<>();
        for (TrackRef t : data.favorites) {
            unique.put(t.getId() + "|" + t.getSourceId(), t);
        }
        for (List<TrackRef> list : data.playlists.values()) {
            for (TrackRef t : list) {
                unique.putIfAbsent(t.getId() + "|" + t.getSourceId(), t);
            }
        }
        return new ArrayList<>(unique.values());
    }

    /**
     * Group all known tracks by artist name.
     * Tracks with empty/unknown artist go into "(Unknown)".
     */
    public Map<String, List<TrackRef>> getAutoGroupByArtist() {
        Map<String, List<TrackRef>> groups = new LinkedHashMap<>();
        for (TrackRef t : getAllTracks()) {
            String key = normalizeGroupKey(t.getArtist());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        return groups;
    }

    /**
     * Group all known tracks by album name.
     * Tracks with empty/unknown album go into "(Unknown)".
     */
    public Map<String, List<TrackRef>> getAutoGroupByAlbum() {
        Map<String, List<TrackRef>> groups = new LinkedHashMap<>();
        for (TrackRef t : getAllTracks()) {
            String key = normalizeGroupKey(t.getAlbum());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        return groups;
    }

    /**
     * Group all known tracks by sourceId (youtube, local, spotify, etc.).
     */
    public Map<String, List<TrackRef>> getAutoGroupBySource() {
        Map<String, List<TrackRef>> groups = new LinkedHashMap<>();
        for (TrackRef t : getAllTracks()) {
            String key = t.getSourceId() != null && !t.getSourceId().isBlank() ? t.getSourceId() : "unknown";
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        return groups;
    }

    private String normalizeGroupKey(String value) {
        if (value == null || value.isBlank()
                || "Unknown Artist".equalsIgnoreCase(value)
                || "Unknown Album".equalsIgnoreCase(value)) {
            return "(Unknown)";
        }
        return value;
    }

    // ─── Play Count & History ─────────────────────────────────────────────

    private static final int MAX_HISTORY = 200;

    /** Record that a track was played. Increments play count and pushes to history. */
    public void recordPlay(TrackRef track) {
        if (track == null) return;
        String key = track.getId(); // Use ID only so counts aggregate across sources
        data.playCounts.merge(key, 1, Integer::sum);
        // Push to history (most recent last, dedup consecutive by ID)
        if (!data.playHistory.isEmpty()) {
            TrackRef last = data.playHistory.get(data.playHistory.size() - 1);
            if (last.getId().equals(track.getId())) {
                save();
                return;
            }
        }
        data.playHistory.add(track);
        // Trim history
        while (data.playHistory.size() > MAX_HISTORY) {
            data.playHistory.remove(0);
        }
        save();
    }

    /** Get play count for a track (aggregated across sources by ID). */
    public int getPlayCount(TrackRef track) {
        if (track == null) return 0;
        return data.playCounts.getOrDefault(track.getId(), 0);
    }

    /** Get most replayed tracks, sorted by play count descending. */
    public List<TrackRef> getMostReplayed() {
        return data.playCounts.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> findTrackByKey(e.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** Get today's play history (most recent last). */
    public List<TrackRef> getTodayHistory() {
        // Return last 50 entries from playHistory (today's plays)
        int size = data.playHistory.size();
        int from = Math.max(0, size - 50);
        return new ArrayList<>(data.playHistory.subList(from, size));
    }

    /** Get full play history (most recent last). */
    public List<TrackRef> getFullPlayHistory() {
        return Collections.unmodifiableList(data.playHistory);
    }

    private TrackRef findTrackByKey(String key) {
        // Search in favorites first
        for (TrackRef t : data.favorites) {
            if (t.getId().equals(key)) return t;
        }
        // Then in playlists
        for (List<TrackRef> list : data.playlists.values()) {
            for (TrackRef t : list) {
                if (t.getId().equals(key)) return t;
            }
        }
        // Then in history
        for (TrackRef t : data.playHistory) {
            if (t.getId().equals(key)) return t;
        }
        return null;
    }
}
