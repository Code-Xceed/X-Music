# CodeX Music Player Roadmap

Detailed product direction: see [PRODUCT_PLAN.md](PRODUCT_PLAN.md).

## Final Direction

Build one stable Fabric `1.21.5` Minecraft music client where:

- `Local` music is the reliability baseline.
- `YouTube` is the primary online native playback source.
- `Spotify` is optional metadata/library work later.
- Playback is native in-game, not browser-first or companion-first.

## Current Priority

Make YouTube search and native playback production-usable as fast as possible.

## Completed Direction Changes

- Project direction moved away from Spotify-first playback.
- YouTube is now the primary online playback target.
- `yt-dlp` is the required YouTube resolution/preparation tool.
- `ffmpeg` is the instant streaming decoder and optional fallback conversion support.
- YouTube first play now prefers FFmpeg PCM streaming instead of waiting for a full download.
- YouTube preparation now prefers native `m4a/aac` cache files before MP3 conversion.
- YouTube search now runs on Enter instead of remote-searching while typing.
- YouTube search now uses a ranked multi-provider pipeline.

## Active Phase: Stable YouTube Native Playback

### Search

- Enter confirms the query.
- Typing only filters/reuses cached results.
- Exact recent queries use cache.
- Stale async results are ignored.
- Results are deduped and ranked for music relevance.

### Preparation

- Direct stream URL is resolved first for uncached tracks.
- Cached file is reused first.
- Native playable format is preferred.
- Conversion is fallback only.
- Tool setup must clearly show `yt-dlp` and `ffmpeg` readiness.

### Playback

- Streamed PCM and prepared files play through the native audio engine.
- Track switching cancels stale work.
- UI states must clearly show searching, downloading, converting, buffering, playing, paused, or error.

## Next Steps

1. Build and test the rebuilt search flow in-game.
2. Verify YouTube results are more relevant for common music queries.
3. Verify Enter-only search behavior feels clean.
4. Test stream-first startup latency.
5. Test cached playback reuse.
6. Improve per-track stream/cache progress.
7. Add safer pre-resolve for likely next tracks.

## Later Work

- NewPipeExtractor-style discovery if it proves more reliable than the current discovery stack.
- Tune FFmpeg PCM buffer thresholds.
- LavaPlayer-style streaming into OpenAL buffers if it outperforms the FFmpeg path.
- Favorites and persistent YouTube cache pinning.
- Spotify metadata/library support if it still adds value after YouTube is stable.
