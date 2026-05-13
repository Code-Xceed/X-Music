# Building And Verification

## Purpose

This file defines the baseline local build commands for Phase 0 work.

## Requirements

- Java toolchain required by the Gradle build
- Network access on first run so Gradle can resolve dependencies

## Recommended Commands

Check Gradle and project configuration:

```powershell
.\gradlew.bat help --console=plain --no-daemon
```

Compile the shared and loader-specific modules:

```powershell
.\gradlew.bat :common:compileJava :fabric:compileJava :forge:compileJava --console=plain --no-daemon
```

## Notes

- The first run may take several minutes because Gradle may download dependencies and toolchains.
- Use `--console=plain` so logs are readable in terminal output and CI logs.
- Treat `common` as the primary module for feature work. `fabric` and `forge` should stay thin.

## Current Status

- Build command documented
- Active test target set to Minecraft `1.21.5`
- Full compile verification still needs to be completed reliably in this workspace
