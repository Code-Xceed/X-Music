# XMUSIC — Dependency & Service Compatibility Matrix

This document maps **every stack layer** in CodeX Music Player against target Minecraft versions.  
Use it before cloning a version folder — not all targets can be “exact copy-paste.”

**Legend:** ✅ Same as 1.21.5 · ⚠️ Pin/version change only · 🔧 Port required · ❌ Not compatible / different product

---

## Target versions

| Code name | MC      | Loader | Planned Java | Copy-paste from 1.21.5? |
|-----------|---------|--------|--------------|-------------------------|
| `1.21.5`  | 1.21.5  | Fabric | 21           | Baseline                |
| `1.21.4`  | 1.21.4  | Fabric | 21           | **Yes** (pins only)     |
| `1.21.x`  | other   | Fabric | 21           | **Yes** (pins only)     |
| `26.1+`   | 26.1…   | Fabric | 21           | **Mostly** (verify)     |
| `1.16.5`  | 1.16.5  | Fabric | 8 or 17*     | **No** — full port      |
| `1.8.9`   | 1.8.9   | ?      | 8            | **No** — legacy rewrite |

\*Recommend **Java 17** for 1.16.5 Fabric (see Java row). Properties file currently says 8.

---

## 1. Build toolchain

| Component | Version (1.21.5) | 1.21.4 | 1.21.x | 26.1+ | 1.16.5 | 1.8.9 |
|-----------|-------------------|--------|--------|-------|--------|-------|
| **Gradle** | 8.14 | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Unimined** | 1.4.1-SNAPSHOT | ✅ | ✅ | ⚠️ Confirm 26.x in WagYourTail releases | ⚠️ 1.16 supported in template | ❌ 1.8.9 = Forge-only in current props |
| **Manifold preprocessor** | 2025.1.14 | ✅ Optional drop for single-version tree | ✅ | ⚠️ | ⚠️ May need older Manifold | ❌ |
| **JVM Downgrader** | 1.3.0 | Not needed (Java 21→21) | Not needed | Not needed | ⚠️ Only if targeting Java 8 bytecode | Required for Java 8 |
| **Forgix** | present | Remove (Fabric-only) | Remove | Remove | Remove | N/A |

**Action:** Each `Versions/<mc>/` owns its own `versionProperties/<mc>.properties`. Do not assume one `fabric_loader` works for all — pins are per MC release.

---

## 2. Loader & Fabric API

| Component | 1.21.5 | 1.21.4 | 26.1+ | 1.16.5 | 1.8.9 |
|-----------|--------|--------|-------|--------|-------|
| **Fabric Loader** | 0.19.2 | ⚠️ 0.19.2 (verify against 1.21.4) | ⚠️ 0.19.2 + 26.1 jar | ⚠️ Use **0.14.x–0.15.x** era, not 0.19 | ❌ No modern Fabric |
| **Fabric API** | 0.128.2+1.21.5 | ⚠️ 0.119.4+1.21.4 | ⚠️ 0.146.1+26.1.2 | ⚠️ 0.42.0+1.16 | ❌ |
| **ClientModInitializer** | ✅ | ✅ | ✅ | ✅ | Legacy Fabric differs |
| **KeyBindingHelper** | ✅ | ✅ | ✅ | ✅ package stable | Different |
| **ClientTickEvents** | ✅ | ✅ | ✅ | ✅ | Different |
| **HudRenderCallback** | ✅ (deprecated in new API) | ✅ | ⚠️ Migrate to HudLayerRegistrationCallback later | 🔧 **MatrixStack**, not GuiGraphics | ❌ |

**Code coupling today:** `XMusicFabric` uses `HudRenderCallback` + `renderTickCounter.getRealtimeDeltaTicks()` — **1.21+ only**.

---

## 3. Minecraft client APIs (largest port surface)

| API / pattern | Used in mod | 1.21.4/5 | 26.1+ | 1.16.5 | 1.8.9 |
|---------------|-------------|----------|-------|--------|-------|
| **GuiGraphics** | All GUI + HUD (~15+ files) | ✅ | ✅ likely | 🔧 Use MatrixStack / DrawableHelper | 🔧 GuiScreen + GL |
| **Screen** | XMusicScreen, HudEditorScreen | ✅ | ✅ | ⚠️ Method signatures differ | 🔧 |
| **KeyMapping** + GLFW | KeyBindings | ✅ | ✅ | 🔧 Was KeyBinding in older yarn | 🔧 |
| **Font** (client) | All text | ✅ | ✅ | ⚠️ Slight draw API changes | 🔧 |
| **PauseScreen** mixin | PauseScreenMixin | ✅ | ✅ | ⚠️ EscapeMenu / different name | 🔧 |
| **Minecraft.getInstance().level** | Playback context | ✅ | ✅ | ✅ | ⚠️ theWorld |
| **InputConstants** | Keybindings | ✅ | ✅ | ✅ | ⚠️ |

**Conclusion:** GUI/HUD is **~40% of source files** and is **not portable** below 1.20+ without a dedicated port.

---

## 4. Java runtime & language APIs

| API | Where used | Min Java | 1.21.x | 1.16.5 (Java 8) | 1.16.5 (Java 17) |
|-----|------------|----------|--------|-----------------|------------------|
| **java.net.http.HttpClient** | YouTubeService, YouTubeToolManager, YouTubeAudioResolver | **11** | ✅ | ❌ | ✅ |
| **ProcessBuilder** | yt-dlp, ffmpeg, downloads | 8 | ✅ | ✅ | ✅ |
| **java.nio.file.*** | Cache, library, tools | 8 | ✅ | ✅ | ✅ |
| **CompletableFuture / Executor** | Async search, downloads | 8 | ✅ | ✅ | ✅ |
| **Records / var / switch expr** | sparse | 14+ | ⚠️ Avoid in shared copies if targeting 8 | ❌ | ✅ |
| **HttpURLConnection** | ArtworkRenderer, Spotify adapter | 8 | ✅ | ✅ | ✅ |

**Critical:** YouTube stack **requires Java 11+** at runtime unless you replace `HttpClient` with OkHttp or `HttpURLConnection` in the 1.16.5 tree.

**Recommendation for 1.16.5:** Ship with **`java_version=17`** in version properties (Fabric 1.16.5 runs fine on 17). That keeps LavaPlayer + YouTube HTTP code without rewriting.

---

## 5. Maven dependencies (shaded / implementation)

| Dependency | Purpose | Java | 1.21.4 | 26.1+ | 1.16.5 | Notes |
|------------|---------|------|--------|-------|--------|-------|
| **gson 2.11.0** | Config, library JSON | 8+ | ✅ | ✅ | ✅ | |
| **jaad 0.8.7** | AAC/M4A decode | 8+ | ✅ | ✅ | ✅ | |
| **soundlibs** (jlayer, mp3spi, vorbis, tritonus) | MP3/OGG | 8+ | ✅ | ✅ | ✅ | Old but works |
| **javasound-flac 1.4.1** | FLAC | 8+ | ✅ | ✅ | ✅ | |
| **lavaplayer 2.2.3** | Stream engine | **11+** | ✅ | ✅ | ✅ if Java 17 | Shaded in fabric jar |
| **lavalink youtube v2 1.16.0** | YT resolve in Java | **11+** | ✅ | ⚠️ Test on 26.x | ✅ if Java 17 | Breaks when YouTube changes |
| **slf4j jcl-over-slf4j 2.0.17** | Logging bridge | 8+ | ✅ | ✅ | ✅ | |

No MC version in Maven coords — compatibility is **Java + shade size + runtime**.

---

## 6. External tools (OS processes)

| Tool | Role | Depends on MC? | All versions |
|------|------|----------------|--------------|
| **yt-dlp** | Search fallback, resolve, download | No | ✅ Same binaries per OS |
| **ffmpeg** | PCM stream decode, convert | No | ✅ Same |
| **cookies.txt** | Optional auth | No | ✅ |

These are **bundled/installed to `.minecraft/xmusic/bin/`** — behavior is identical across version folders if code paths are the same.

---

## 7. In-mod services (logic layers)

| Service | 1.21.4 copy? | 1.16.5 | 26.1+ | Notes |
|---------|--------------|--------|-------|-------|
| **ConfigManager / XMusicConfig** | ✅ | ✅ | ✅ | Gson only |
| **LocalMusicService** | ✅ | ✅ | ✅ | File scan |
| **AudioEngine + OpenALOutput** | ✅ | ⚠️ Tick on render thread — verify AL API | ✅ | LWJGL AL10 |
| **AudioPlayer** | ✅ | ✅ | ✅ | |
| **Decoders** (Mp3, Aac, Ogg, JavaSound) | ✅ | ✅ | ✅ | |
| **PlayerFacade** | ✅ | ✅ | ✅ | Logic-only |
| **NativeAudioBackend** | ✅ | ✅ | ✅ | |
| **YouTubeNativeBackend** | ✅ | ✅* | ✅ | *if Java 11+ |
| **YouTubeService** (search pipeline) | ✅ | ✅* | ✅ | HttpClient |
| **YouTubeToolManager** | ✅ | ✅* | ✅ | Auto-install |
| **YouTubeDownloadManager** | ✅ | ✅ | ✅ | |
| **YouTubeStreamResolver / FfmpegPcmStream** | ✅ | ✅ | ✅ | |
| **LavaPlayerEngine / Backend** | ✅ | ✅* | ⚠️ Test | Optional path |
| **LavaSearchService** | ✅ | ✅* | ⚠️ Test | |
| **SpotifyAuthService** | ✅ stub | ✅ | ✅ | Not used |
| **SpotifyMusicSourceAdapter** | ✅ | ✅ | ✅ | HttpURLConnection |
| **DownloadManager** | ✅ | ✅ | ✅ | |
| **LibraryManager** | ✅ | ✅ | ✅ | |
| **I18n** | ✅ | ✅ | ✅ | |
| **FolderWatcher** | ✅ | ✅ | ✅ | |
| **ServiceManager** | ✅ | ✅ | ✅ | Wiring only |

---

## 8. UI / frontend (not the same across versions)

| Area | Files (approx) | 1.21.4 | 1.16.5 | 26.1+ |
|------|----------------|--------|--------|-------|
| Main screen | XMusicScreen, ContentHost, tabs | ✅ copy | 🔧 rewrite draw | ✅ likely |
| Player bar | PlayerBar (~840 LOC) | ✅ | 🔧 | ✅ |
| Settings | SettingsTab (~1189 LOC) | ✅ | 🔧 | ✅ |
| HUD | MiniPlayerOverlay, HudRenderer | ✅ | 🔧 | ⚠️ new HUD API |
| Theme / render helpers | GuiRender, IconRenderer, ArtworkRenderer | ✅ | 🔧 | ✅ |
| Mixins | PauseScreenMixin | ✅ | ⚠️ target class | ✅ |

---

## 9. Version-tier summary

### Tier A — **Clone + Gradle pins only** (your next step)

- 1.21.4, 1.21.5, 1.21.3 … 1.21.11 (same Java 21, same GuiGraphics era)

**Allowed diffs:** `gradle.properties`, `versionProperties/*`, `fabric.mod.json` expansion, docs.

**Known 1.21.4 API delta:** `ArtworkRenderer` uses `new DynamicTexture(image)` — 1.21.5 uses `new DynamicTexture(() -> name, image)` (label constructor added in newer MC).

### Tier B — **Clone + smoke test** (26.1+)

- Same Java 21 and modern GUI
- Verify: Unimined 26.1 mappings, Fabric API jar, `HudRenderCallback` vs `HudLayerRegistrationCallback`, MC class renames

**Risk:** Low–medium. Expect 0–small source fixes, not a rewrite.

### Tier C — **Port** (1.16.5 Fabric)

| Workstream | Effort |
|------------|--------|
| Replace GuiGraphics → 1.16 draw API | Large |
| Fabric HUD / keybinds for 1.16 | Medium |
| Fix Fabric loader/API pins | Small |
| Java 17 vs 8 decision + HttpClient | Medium if Java 8 |
| Re-test OpenAL + threading | Small |
| YouTube + local + queue logic | Small (copy as-is if Java 17) |

**Cannot promise** same GUI pixels without reimplementation.

### Tier D — **Not a copy** (1.8.9)

- Current repo has **no Fabric** profile for 1.8.9 (`builds_for=forge` only).
- **Legacy Fabric** exists but APIs differ radically from 1.21.
- Treat as **separate legacy project** if still required; not part of “exact copy” plan.

---

## 10. Recommended implementation order

1. **1.21.5** — baseline in `Versions/1.21.5/` (done)
2. **1.21.4** — Tier A clone after Forge removal
3. **Other 1.21.x** — Tier A as needed
4. **26.1+** — Tier B one folder per release line
5. **1.16.5** — Tier C scoped port (Java 17 + GUI rewrite)
6. **1.8.9** — Defer or spin `Versions/1.8.9-legacy/` with separate design doc

---

## 11. Per-version checklist (before marking “supported”)

- [ ] `.\gradlew.bat :fabric:build` succeeds
- [ ] Client launches on target MC
- [ ] Open UI (M), all tabs render
- [ ] Local file plays (mp3 + one other format)
- [ ] YouTube: Enter search → result → play (yt-dlp + ffmpeg ready)
- [ ] HUD mini-player visible and draggable
- [ ] Keybinds: play/pause, next/prev, volume
- [ ] No crash on world join / pause menu mixin

---

## 12. What “exact copy” means per tier

| Tier | Meaning |
|------|---------|
| A | Byte-identical `common/src` + `fabric/src` except Gradle/resources |
| B | Same sources; allow fixes for MC 26 API deltas |
| C | Same **features** and **backend behavior**, new GUI/HUD implementation |
| D | Same **product goals**, new codebase |

This matches your clarification: **things are not the same for all versions** — only Tier A targets are true duplicates.
