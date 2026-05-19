package com.codexceed.xmusic.source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for normalized music sources.
 */
public final class SourceRegistry {
    private final Map<String, MusicSource> sources = new LinkedHashMap<>();

    public void register(MusicSource source) {
        if (source == null) return;
        sources.put(source.getId(), source);
    }

    public MusicSource get(String id) {
        return sources.get(id);
    }

    public List<MusicSource> getAll() {
        return new ArrayList<>(sources.values());
    }
}
