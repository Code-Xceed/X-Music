# CodeX Music Player GUI Production Plan

## Purpose

This document defines the production direction for the new CodeX Music Player GUI.
The previous GUI has been detached and removed. The new GUI must be rebuilt from
scratch component by component, with compile/build verification after meaningful
milestones.

The goal is a compact, minimal, Minecraft-native music client UI that supports
YouTube/local playback now and can later support groups, library, likes/favorites,
native downloads, download groups, and source switching.

## Reference Design Analysis

The provided reference image has a strong Minecraft identity and a clear music
app structure. It uses a persistent sidebar, app frame, source switching, track
cards, recommendations, and a bottom playback bar.

Traits worth keeping:

- Minecraft-inspired materials: dark stone, moss/green accent, block-style icons.
- Persistent bottom player bar.
- Left navigation for main areas.
- Top search/source controls.
- Download affordance on tracks.
- Clear page hierarchy.
- Compact cards for highlighted content.

Traits to avoid:

- Heavy borders and bevels on every element.
- Too much texture noise inside content areas.
- Oversized artwork cards that waste space.
- Large decorative sections that reduce usable track density.
- Spotify/YouTube branding as primary chrome before both sources are fully supported.
- One large screen file mixing rendering, state, search, downloads, and playback.

## Product Direction

The GUI should feel like a native Minecraft mod, not a web dashboard pasted into
Minecraft. It should be compact, fast to scan, and practical during gameplay.

Core principles:

- Compact first.
- Minimal visual noise.
- Strong component boundaries.
- One primary action per visible area.
- Every view handles loading, empty, error, and disabled states.
- GUI code does not directly own playback, library, or download logic.
- Build one component at a time.
- Compile/build after each meaningful milestone.

## Visual Style

The style should be Minecraft-inspired but cleaner than the reference image.

Recommended style:

- Dark charcoal base.
- Subtle stone/wood texture only on the outer frame or selected controls.
- Green accent for active state, playback progress, and positive actions.
- Red only for YouTube identity or destructive actions.
- Low-contrast gray/brown borders.
- Small, readable text.
- Compact spacing.
- No modern glassmorphism, oversized radius, or floating card stacks.

Color roles:

- Background: near-black transparent overlay.
- Surface 1: main dark panel.
- Surface 2: raised list/card surface.
- Border: low-contrast stone gray.
- Accent: grass/lime green.
- Danger: red.
- Warning: amber.
- Text primary: white.
- Text secondary: light gray.
- Text muted: gray.

Typography:

- Use Minecraft-style text where appropriate.
- Keep headings small and useful.
- Track titles and artist names must truncate cleanly.
- No viewport-scaled font sizes.
- No negative letter spacing.

## Main Shell Layout

The GUI should use one persistent shell:

```text
+------------------------------------------------------+
| Header: app/search/source/status/close                |
+-------------+----------------------------------------+
| Sidebar     | Active View                            |
| Home        |                                        |
| Search      |                                        |
| Library     |                                        |
| Groups      |                                        |
| Downloads   |                                        |
| Settings    |                                        |
+-------------+----------------------------------------+
| Persistent Player Bar                                |
+------------------------------------------------------+
```

Recommended layout constraints:

- Centered modal over Minecraft.
- Width: about 80-86% of screen.
- Height: about 76-82% of screen.
- Sidebar width: 120-150px.
- Bottom player height: 48-64px.
- Border radius: 0-4px.
- Compact mode collapses sidebar to icons if width is low.

## Navigation

Primary nav items:

- Home
- Search
- Library
- Groups
- Downloads
- Settings

Rules:

- Sidebar owns navigation only.
- Active view is highlighted with a green strip.
- Normal width uses icon + label.
- Compact width uses icon only.
- No nested sidebar menus in the first implementation.

## Component Inventory

### 1. GuiShell

Purpose:

- Owns the frame, layout, routing outlet, and close behavior.
- Hosts `TopBar`, `SidebarNav`, active view, and `PlayerBar`.

Responsibilities:

- Center and size the GUI.
- Handle responsive/compact mode.
- Route active views.
- Close on Esc.
- Keep the Minecraft world visible behind the overlay.

Must not:

- Execute searches.
- Manage download jobs.
- Own playback queue logic.
- Render track rows directly.

### 2. TopBar

Purpose:

- Provide global context and high-level controls.

Elements:

- App name.
- Optional compact search entry/search button.
- Source segmented control: YouTube / Spotify.
- Status pill: online/offline/tools missing/downloading.
- Close button.

Rules:

- Spotify may be visible but disabled until the backend exists.
- Source toggle must not claim unsupported features work.
- Close button is always visible.

### 3. SidebarNav

Purpose:

- Main view navigation.

Elements:

- Home
- Search
- Library
- Groups
- Downloads
- Settings

States:

- Default.
- Hovered.
- Active.
- Disabled if a view is not ready.

### 4. PlayerBar

Purpose:

- Persistent playback control surface.

Elements:

- Artwork thumbnail or source icon.
- Track title.
- Artist/source.
- Previous.
- Play/pause.
- Next.
- Seek bar.
- Current time.
- Duration.
- Volume.
- Queue button.
- Like/favorite button.
- Download button.

States:

- Idle.
- Resolving.
- Buffering.
- Playing.
- Paused.
- Failed.

Rules:

- Always visible.
- Does not resize based on track text.
- Long title/artist text truncates.
- Controls are disabled when no track is active.

### 5. TrackRow

Purpose:

- Primary reusable list row for tracks.

Elements:

- Small artwork/source icon.
- Title.
- Artist/source subtitle.
- Duration.
- Like button.
- Download button.
- More menu.
- Optional selection indicator.

Used by:

- Search results.
- Library.
- Group detail.
- Downloads.
- Queue.

Interactions:

- Single click selects.
- Double click or play icon plays.
- Download button starts download.
- Like button toggles favorite.
- More menu opens contextual actions.

### 6. TrackCard

Purpose:

- Compact card for Home highlights only.

Elements:

- Artwork.
- Title.
- Artist.
- Download status icon.
- Like state.

Rules:

- Use sparingly.
- Do not make every page card-heavy.
- Card sizes must be fixed and responsive.

### 7. SearchView

Purpose:

- Search music sources.

Initial support:

- YouTube search.
- Spotify toggle visible but disabled until restored.

Elements:

- Search input.
- Source segmented control.
- Results list.
- Loading state.
- Empty state.
- Error state.

Behavior:

- Debounced search input.
- Search results use `TrackRow`.
- Source switch changes search provider only when supported.
- Failed search should show a readable error without crashing the GUI.

### 8. LibraryView

Purpose:

- Browse saved and local music.

Sections:

- Local music.
- Saved YouTube tracks.
- Recently played.
- Liked tracks.

Controls:

- Rescan local.
- Sort.
- Filter.
- Play all.

States:

- Empty library.
- Scanning local files.
- Scan failed.
- Loaded.

### 9. GroupsView

Purpose:

- Manage user groups and system groups.

Group types:

- User-created groups.
- Likes/Favorites group.
- Downloads group.
- Recently played group.

Group item elements:

- Icon/art.
- Name.
- Track count.
- Last updated.
- Play button.
- More menu.

Rules:

- System groups cannot be deleted.
- User groups can be renamed/deleted.
- Groups are the long-term playlist/favorites foundation.

### 10. GroupDetailView

Purpose:

- Show and manage tracks inside a group.

Controls:

- Play all.
- Shuffle.
- Add tracks.
- Rename group.
- Delete group.
- Download all, later.

Rules:

- Rename/delete only for user groups.
- Likes/Favorites group uses unlike/remove behavior.
- Downloads group reflects actual downloaded tracks.

### 11. DownloadsView

Purpose:

- Native download system UI.

Sections:

- Active downloads.
- Completed downloads.
- Failed downloads.
- Cached tracks.

Download row elements:

- Track title.
- Source.
- Progress.
- Status.
- Retry.
- Cancel.
- Delete.
- Open folder if supported by platform helper.

States:

- Queued.
- Resolving.
- Downloading.
- Converting.
- Completed.
- Failed.
- Canceled.

Rules:

- Download actions must never block the render thread.
- Failed downloads must be retryable.
- The Downloads group should be backed by real download state.

### 12. SettingsView

Purpose:

- Production-needed configuration only.

Initial settings:

- YouTube tool status: yt-dlp and ffmpeg.
- Cache size.
- Local music folder.
- Volume defaults.
- Keybind information.
- Debug/log export later.

Rules:

- Do not expose unfinished experimental settings.
- Settings changes should be explicit and saved safely.

## Interaction Model

Keyboard:

- Esc closes GUI.
- Space toggles play/pause only when not typing.
- Arrow/list navigation can be added later.

Mouse:

- Single click selects.
- Double click plays.
- Buttons use clear hover/pressed states.
- More menus are contextual.

Search:

- Debounced input.
- Loading indicator appears quickly.
- Empty state appears when no results.
- Errors are shown inline.

Downloads:

- Download button starts a background job.
- Progress updates without freezing UI.
- Retry/cancel/delete are explicit actions.

## Data And State Boundaries

The GUI should consume view models, not raw service classes directly.

Suggested state objects:

- `GuiRoute`
- `PlaybackViewState`
- `TrackViewModel`
- `GroupViewModel`
- `DownloadViewModel`
- `SearchViewState`
- `LibraryViewState`

Suggested controller actions:

- `play(track)`
- `pauseResume()`
- `previous()`
- `next()`
- `seek(positionMs)`
- `setVolume(volume)`
- `search(query, source)`
- `toggleLike(track)`
- `download(track)`
- `createGroup(name)`
- `renameGroup(group, name)`
- `deleteGroup(group)`
- `addToGroup(track, group)`
- `removeFromGroup(track, group)`

Rules:

- Rendering components do not call services directly.
- Controllers translate GUI actions into player/library/download operations.
- View models protect the GUI from null or partial backend state.

## Empty, Loading, Error, Disabled States

Every view and major component needs explicit states.

Required states:

- Empty: no data yet.
- Loading: data request is active.
- Error: operation failed with readable reason.
- Disabled: feature visible but not available.
- Offline/tools missing: backend cannot complete action.

Examples:

- SearchView empty: prompt to search.
- SearchView loading: compact spinner/progress text.
- SearchView error: source unavailable or request failed.
- DownloadsView empty: no downloads yet.
- PlayerBar idle: no track active.
- Spotify toggle disabled: backend not implemented yet.

## Build Order

The GUI must not be created in one shot. Build in this order:

1. Recreate minimal `GuiShell`.
2. Add `TopBar`.
3. Add `SidebarNav`.
4. Add empty routed views.
5. Build and verify.
6. Add `PlayerBar`.
7. Build and verify.
8. Add `TrackRow`.
9. Build and verify.
10. Build `SearchView`.
11. Build and verify.
12. Build `LibraryView`.
13. Build and verify.
14. Build `GroupsView`.
15. Build `GroupDetailView`.
16. Build and verify.
17. Build `DownloadsView`.
18. Build and verify.
19. Build `SettingsView`.
20. Add polish: compact mode, icons, hover states, empty/error states.
21. Final build verification.

## Acceptance Criteria

The GUI is production-ready only when:

- The mod builds successfully.
- No screen crashes on missing service/data state.
- All views have loading, empty, error, and disabled states.
- Text truncates instead of overlapping.
- The old GUI classes do not return.
- Source toggle does not claim Spotify works until the backend exists.
- Player bar accurately reflects backend playback state.
- Downloads UI reflects real download manager state.
- UI remains usable at smaller Minecraft window sizes.
- Components are split into focused files.
- Render thread is not blocked by search, download, or tool setup work.

## First Implementation Milestone

The first implementation milestone should be:

- `GuiShell`
- `TopBar`
- `SidebarNav`
- Empty routed views
- Close behavior
- Open GUI keybind restored only after the shell exists
- Build verification

No player/search/library/download functionality should be added in the first
milestone. The first milestone exists to establish the new architecture and
visual frame safely.
