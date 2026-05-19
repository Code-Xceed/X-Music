# XMUSIC Multi-Version Plan

## Goals

1. **Fabric only** — remove Forge/NeoForge from all active work.
2. **Separate implementation per version** — each `Versions/<mc>/` is a full copy of the mod (common + fabric + build). No shared code module across versions for 1.21.x twins.
3. **Identical product per version** — same GUI, features, backends, and behavior; only build pins and mappings differ where MC requires it.
4. **Monorepo layout** — `Versions/1.21.5/` is canonical; new versions are copied from it.

## Target versions

| Priority | MC version | Loader | Java | Notes |
|----------|------------|--------|------|-------|
| Done     | 1.21.5     | Fabric | 21 | Baseline in `Versions/1.21.5/` |
| Next     | 1.21.4     | Fabric | 21 | Exact folder copy; change pins only |
| Later    | 1.21.x+    | Fabric | 21 | One folder per release line as needed |
| Later    | 1.16.5     | Fabric | 8  | API port (GuiGraphics, etc.) — not a literal file copy |
| Later    | 26.1+      | Fabric | 21 | Copy from 1.21.5, update Unimined pins |
| Special  | 1.8.9      | —      | 8  | **Not** a copy of 1.21.5; see below |

### 1.8.9 note

Modern mod code (1.21 GUI, OpenAL path, LavaPlayer stack) cannot be copy-pasted to 1.8.9. Options:

- **Legacy Fabric** for 1.8.9 (separate port, different APIs), or
- Defer 1.8.9 until a dedicated legacy branch is scoped.

Forge is **out of scope** per product direction.

---

## Repository layout (current + future)

```
XMUSIC/                          # repo root (X-Music)
├── README.md
├── docs/
│   └── VERSIONING_PLAN.md       # this file
├── Versions/
│   ├── 1.21.5/                  # ✅ baseline
│   │   ├── gradlew / gradlew.bat
│   │   ├── settings.gradle
│   │   ├── gradle.properties    # mc_ver=1.21.5
│   │   ├── versionProperties/
│   │   ├── build.gradle
│   │   ├── buildSrc/
│   │   ├── common/
│   │   └── fabric/
│   └── 1.21.4/                  # 🔜 full duplicate of 1.21.5
└── .gitignore
```

Each version folder is **Gradle-rooted** (own wrapper, own `settings.gradle`). No composite root `settings.gradle` that builds all versions at once (keeps isolation and “exact copy” discipline).

---

## Phase 0 — Monorepo structure ✅

- [x] Create `Versions/1.21.5/`
- [x] Move existing project into it (`git mv`)
- [x] Root `README.md` + monorepo `.gitignore`
- [x] Update `Versions/1.21.5/BUILDING.md` paths

**Build from:** `Versions/1.21.5/`

---

## Phase 1 — Fabric-only cleanup (1.21.5)

Do this **before** cloning to 1.21.4 so both copies start clean.

| Step | Action |
|------|--------|
| 1.1 | Delete `Versions/1.21.5/forge/` |
| 1.2 | `settings.gradle`: `builds_for=fabric` only (via `1.21.5.properties`) |
| 1.3 | Remove `buildSrc/.../unimined-forge.gradle`, `unimined-neoforge.gradle` |
| 1.4 | Remove Forgix plugin from `build.gradle` if unused |
| 1.5 | Trim `common.gradle` `processResources` — drop `mods.toml` / forge expand props |
| 1.6 | Grep for `Forge`, `forge_loader`, `neoforge` — remove dead references |
| 1.7 | `.\gradlew.bat :fabric:build` — must succeed |

Optional: remove unused `versionProperties/*.properties` except `1.21.5.properties` in the 1.21.5 tree (single-version folder doesn’t need 24 MC profiles).

---

## Phase 2 — Add 1.21.4 (exact copy)

### 2.1 Create tree

```powershell
cd XMUSIC
xcopy /E /I Versions\1.21.5 Versions\1.21.4
# Remove build artifacts from copy:
Remove-Item -Recurse -Force Versions\1.21.4\.gradle, Versions\1.21.4\build -ErrorAction SilentlyContinue
```

Use `robocopy` or `git` after initial commit: prefer **`git clone` workflow** — copy tracked files only, not `.gradle`/`build`.

### 2.2 Allowed diffs (only these files)

| File | Change |
|------|--------|
| `gradle.properties` | `mc_ver=1.21.4` |
| `versionProperties/1.21.4.properties` | Already correct: MC 1.21.4, `fabric_api_version=0.119.4+1.21.4`, `builds_for=fabric` |
| `versionProperties/1.21.5.properties` | Delete from 1.21.4 tree OR keep but unused |
| `BUILDING.md` | Title → 1.21.4 |
| `fabric.mod.json` | Expands from properties — no hand edit if `compatible_mc_versions` is `["1.21.4"]` |

**No Java/source/resource changes** unless compile proves otherwise (unlikely between 1.21.4 and 1.21.5).

### 2.3 Simplify 1.21.4 Gradle (recommended)

- Keep only `versionProperties/1.21.4.properties`
- Set `gradle.properties`: `mc_ver=1.21.4`
- Remove Manifold `MC_VER` multi-version `build.properties` generation if unused in single-version tree

### 2.4 Verify

```powershell
cd Versions\1.21.4
.\gradlew.bat :fabric:build --console=plain --no-daemon
```

In-game smoke test on **1.21.4** client: open UI (M), local play, YouTube search (Enter), playback, HUD.

### 2.5 Jar naming

Output: `xmusic-fabric-1.0.0-1.21.4.jar` (from `mod_version` + `minecraft_version` in `common.gradle`).

---

## Phase 3+ — Future versions

### 1.21.x line

For each new patch (e.g. 1.21.6): copy `Versions/1.21.5` → `Versions/1.21.6`, pin properties, build, test. Same Java 21 + same code.

### 1.16.5 (Fabric)

- Copy `Versions/1.21.5` as starting point **only for project layout**, then port:
  - `GuiGraphics` → `MatrixStack` / older draw APIs
  - `Screen` / input / HUD hooks for 1.16 mappings
  - Java 8 language level + dependencies compatible with 1.16
- Expect **weeks** of port work — not a copy-paste day.

### 26.1+ (Fabric)

- Copy from `1.21.5`, set `mc_ver` from `versionProperties/26.1.properties`
- Re-verify Fabric API + Unimined compatibility
- Minimal code changes expected if Mojang mappings stay stable

---

## What we are NOT doing

- Single Gradle project with `-Pmc_ver=` switching (old model) for release builds
- Shared `common` library across MC versions
- Forgix multi-loader jars
- Forge / NeoForge loaders
- “One codebase, `#if MC_1_21_5`” preprocessor for 1.21.4 vs 1.21.5 (unnecessary for patch twins)

---

## CI (later)

```yaml
matrix:
  version: [1.21.5, 1.21.4]
steps:
  - run: cd Versions/${{ matrix.version }} && ./gradlew :fabric:build
```

---

## Checklist: “exact copy” definition

For 1.21.4 vs 1.21.5:

- [ ] `diff -rq` of `common/src` and `fabric/src` is empty (after clone, before any port)
- [ ] Only `gradle.properties`, `versionProperties/*`, `BUILDING.md`, and docs differ
- [ ] Same mod version string unless you intentionally bump
- [ ] Same assets, lang, mixins JSON structure
- [ ] Both produce working Fabric jars on their MC version

---

## Immediate next actions

1. Confirm `Versions/1.21.5` builds from new path.
2. Execute **Phase 1** (Forge removal) on 1.21.5.
3. Execute **Phase 2** (1.21.4 folder copy + pins).
4. Add `Versions/1.21.4` to root `README.md` matrix.
