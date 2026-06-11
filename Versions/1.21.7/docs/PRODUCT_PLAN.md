# CodeX Music Player Product Plan

## Final Product Direction

CodeX Music Player is now a native Minecraft music client with this priority order:

1. `Local` is the always-reliable baseline.
2. `YouTube` is the primary online search and playback source.
3. `Spotify` is optional metadata/library support later, not the core playback promise.

The product goal is simple:

1. User installs the Fabric mod.
2. User opens Minecraft.
3. User opens the music UI.
4. User searches YouTube or selects local music.
5. Audio plays natively in-game.

## Non-Negotiable Standards

- Minecraft must never freeze while searching, downloading, converting, or buffering.
- YouTube search must only perform remote work when the user confirms the query with Enter.
- Search results must stay stable while typing, switching tabs, or starting playback.
- Playback must use the native in-game audio path.
- Download/conversion state must be honest and visible.
- No hidden browser/player runtime is part of the main YouTube playback flow.
- No Spotify workaround or scraping path is allowed to become the core reliability plan.

## Chosen Architecture

The project follows a three-layer model.

### Discovery

Responsibilities:

- search
- result ranking
- metadata
- thumbnails
- cached result reuse

Current production path:

- fast InnerTube-style YouTube search first
- `yt-dlp` search fallback when the local tool is ready
- public web result fallback
- optional YouTube Data API fallback for developer builds

Future improvement:

- NewPipeExtractor-style discovery can replace or supplement the current discovery layer if it proves more stable in practice.

### Resolution

Responsibilities:

- turn a selected YouTube result into a playable stream or local source
- keep cache entries keyed by video id
- reuse cached files before doing network work
- prefer direct stream startup for uncached tracks
- prefer native playable formats for cache reuse

Current production path:

- `yt-dlp` resolves a short-lived direct media URL for the selected video
- FFmpeg opens that URL and decodes it to raw PCM for fast first sound
- cached local preparation runs as fallback and background reuse
- native `m4a/aac` cache is preferred
- `ffmpeg -> mp3` conversion is fallback only when a local cached file is needed

### Audio Bridge

Responsibilities:

- play stream-first PCM and prepared files through the existing native audio engine
- preserve pause, resume, seek, stop, volume, queue, and state reporting
- avoid overlap when switching tracks

Current production path:

- direct stream URL -> FFmpeg PCM stdout -> `AudioEngine` -> OpenAL output
- cached/prepared file -> `AudioPlayer` / `AudioEngine` -> OpenAL output

Future improvement:

- LavaPlayer-style streamed decode into OpenAL buffers for faster start and true play-while-loading.

## YouTube Search Rules

- Typing does not trigger remote search.
- Enter starts the real backend search.
- Exact recent queries are served from cache.
- Stale search results are ignored when a newer query finishes later.
- Results are deduped by video id.
- Ranking favors exact/relevant music results and penalizes reactions, tutorials, shorts, huge loops, and unrelated uploads.
- The UI can locally filter cached results while the user edits text, but it does not hit the network until Enter.

## YouTube Playback Rules

- `yt-dlp` is required for online YouTube playback.
- `ffmpeg` is required for instant stream-first playback and optional only for cached fallback conversion.
- If a native playable file is already cached, playback starts from cache.
- If cache is missing, the pipeline resolves a direct stream first and caches in the background.
- If native `m4a/aac` preparation fails, MP3 conversion is attempted only if `ffmpeg` is present.
- Failures must show a useful in-game state rather than hanging.

## Phase Plan

### Phase 1: Stable Native YouTube Product

- rebuild search around deliberate Enter-based queries
- improve result relevance and dedupe
- stabilize tool readiness checks
- keep `yt-dlp` as the required resolution/preparation tool
- use FFmpeg PCM streaming as the fast first-play path
- make native playable cache the first path
- keep conversion as fallback only
- ensure Fabric `1.21.5` jar builds cleanly

### Phase 2: Playback Speed

- prefetch likely next tracks
- improve cache eviction and reuse
- prepare selected track with clearer progress
- complete background cache while streaming is already playing

### Phase 3: Streaming Engine

- evaluate LavaPlayer-style streamed decode
- feed decoded PCM into OpenAL buffer queues
- reduce first-sound latency
- support play-while-loading cleanly

### Phase 4: Optional Integrations

- Spotify metadata/library can return later
- Spotify playback remains official Connect/device control only if exposed
- Spotify is not required for the product to be useful

## Current Implementation Status

- Local playback exists and remains the stable baseline.
- YouTube tool bootstrap exists with `yt-dlp` required and `ffmpeg` optional.
- YouTube playback now prefers stream-first FFmpeg PCM output for first play.
- YouTube cache preparation still prefers native playable cache files before conversion.
- YouTube search has been rebuilt to use an Enter-confirmed multi-provider search pipeline.
- The old visible Spotify runtime direction is no longer the main product path.
