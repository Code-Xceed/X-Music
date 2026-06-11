# GUI Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reintroduce only the first GUI milestone: shell, top bar, sidebar, empty routed views, placeholder player bar, and open/close wiring.

**Architecture:** The GUI is split into small render components. `XMusicScreen` owns the Minecraft `Screen`; layout, theme, route, and components are separate classes so later views can be added without growing one large file.

**Tech Stack:** Java 21, Minecraft `Screen`/`GuiGraphics`, Fabric/Forge keybind hooks, Gradle Fabric build.

---

### Task 1: Foundation Types

**Files:**
- Create: `common/src/main/java/com/codexceed/xmusic/gui/GuiRoute.java`
- Create: `common/src/main/java/com/codexceed/xmusic/gui/layout/GuiFrame.java`
- Create: `common/src/main/java/com/codexceed/xmusic/gui/theme/GuiTheme.java`

- [ ] Add route enum for Home, Search, Library, Groups, Downloads, Settings.
- [ ] Add immutable frame geometry for shell, top bar, sidebar, content, and player bar.
- [ ] Add theme tokens for colors and spacing.

### Task 2: Render Helpers

**Files:**
- Create: `common/src/main/java/com/codexceed/xmusic/gui/render/GuiRender.java`

- [ ] Add rectangle, outline, text, centered text, and truncation helpers.
- [ ] Keep the helper Minecraft-version-light and easy to replace.

### Task 3: Components

**Files:**
- Create: `common/src/main/java/com/codexceed/xmusic/gui/component/TopBar.java`
- Create: `common/src/main/java/com/codexceed/xmusic/gui/component/SidebarNav.java`
- Create: `common/src/main/java/com/codexceed/xmusic/gui/component/ContentHost.java`
- Create: `common/src/main/java/com/codexceed/xmusic/gui/component/PlayerBar.java`

- [ ] Render static shell components.
- [ ] Add hover/active states for route buttons.
- [ ] Keep view bodies as empty production placeholders.

### Task 4: Screen And Keybind Wiring

**Files:**
- Create: `common/src/main/java/com/codexceed/xmusic/gui/screen/XMusicScreen.java`
- Modify: `common/src/main/java/com/codexceed/xmusic/input/KeyBindings.java`
- Modify: `common/src/main/resources/assets/xmusic/lang/en_us.json`
- Modify: `fabric/src/main/java/com/codexceed/xmusic/XMusicFabric.java`
- Modify: `forge/src/main/java/com/codexceed/xmusic/XMusicForge.java`

- [ ] Restore the open GUI keybind.
- [ ] Open/close `XMusicScreen` from Fabric and Forge.
- [ ] Keep playback hotkeys unchanged.

### Task 5: Verification

**Commands:**
- Run `.\gradlew.bat :common:compileJava :fabric:compileJava --console=plain --no-daemon --stacktrace --max-workers=2`.
- Run `.\gradlew.bat :fabric:build --offline --console=plain --no-daemon --stacktrace --max-workers=2` if compile passes.

**Expected:**
- Java compilation passes.
- Fabric jar builds.
