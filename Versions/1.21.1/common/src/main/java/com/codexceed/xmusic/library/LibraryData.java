package com.codexceed.xmusic.library;

import com.codexceed.xmusic.source.TrackRef;
import java.util.*;

public class LibraryData {
    public Set<TrackRef> favorites = new LinkedHashSet<>();
    public Map<String, List<TrackRef>> playlists = new LinkedHashMap<>();
    public Set<TrackRef> downloaded = new LinkedHashSet<>();
    public Map<String, Integer> playCounts = new LinkedHashMap<>();
    public List<TrackRef> playHistory = new ArrayList<>();
}
