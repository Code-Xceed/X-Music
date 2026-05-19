# CodeX Music Player (XMUSIC)

Multi-version monorepo. Each Minecraft version is a **fully separate, self-contained project** under `Versions/`.

## Layout

```
XMUSIC/
├── README.md                 ← this file
├── Versions/
│   ├── 1.21.5/               ← active baseline (Fabric, MC 1.21.5)
│   ├── 1.21.4/               ← exact copy, Fabric MC 1.21.4
│   └── …                     ← future: 1.16.5, 1.26+, etc.
└── .gitignore
```

## Build a version

```powershell
cd Versions/1.21.5
.\gradlew.bat :fabric:build --console=plain --no-daemon
```

See `Versions/<version>/BUILDING.md` for per-version notes.

## Version matrix (planned)

| Folder     | Minecraft | Loader | Java | Status        |
|-----------|-----------|--------|------|---------------|
| `1.21.5`  | 1.21.5    | Fabric | 21   | **Active**    |
| `1.21.4`  | 1.21.4    | Fabric | 21   | **Ready** (verify build) |
| `1.16.5`  | 1.16.5    | Fabric | 8    | Later         |
| `1.26.x`  | 26.1+     | Fabric | 21   | Later         |

All versions target **Fabric only** (Forge removed from new work).
