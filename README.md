# CodeX Music Player (XMUSIC)

Multi-version monorepo. Each Minecraft version is a **fully separate, self-contained project** under `Versions/`.

## Layout

```
XMUSIC/
├── README.md                 ← this file
├── Versions/
│   ├── 1.21.5/               ← active baseline (Fabric, MC 1.21.5)
│   ├── 1.21/                 ← Fabric MC 1.21
│   ├── 1.21.1/               ← Fabric MC 1.21.1
│   ├── 1.21.2/               ← Fabric MC 1.21.2
│   ├── 1.21.3/               ← Fabric MC 1.21.3
│   ├── 1.21.4/               ← Fabric MC 1.21.4
│   ├── 1.21.6/               ← Fabric MC 1.21.6
│   ├── 1.21.7/               ← Fabric MC 1.21.7
│   ├── 1.21.8/               ← Fabric MC 1.21.8
│   ├── 1.21.9/               ← Fabric MC 1.21.9
│   ├── 1.21.10/              ← Fabric MC 1.21.10
│   ├── 1.21.11/              ← Fabric MC 1.21.11
│   ├── 26.1/                 ← Fabric MC 26.1
│   ├── 26.1.1/               ← Fabric MC 26.1.1
│   └── 26.1.2/               ← Fabric MC 26.1.2
└── .gitignore
```

## Build a version

```powershell
cd Versions/1.21.5
.\gradlew.bat :fabric:build --console=plain --no-daemon
```

See `Versions/<version>/BUILDING.md` for per-version notes.

## Version matrix

| Folder     | Minecraft | Loader | Java | Status        |
|-----------|-----------|--------|------|---------------|
| `1.21`    | 1.21      | Fabric | 21   | **Active**    |
| `1.21.1`  | 1.21.1    | Fabric | 21   | **Active**    |
| `1.21.2`  | 1.21.2    | Fabric | 21   | **Active**    |
| `1.21.3`  | 1.21.3    | Fabric | 21   | **Active**    |
| `1.21.4`  | 1.21.4    | Fabric | 21   | **Active**    |
| `1.21.5`  | 1.21.5    | Fabric | 21   | **Active** (Baseline) |
| `1.21.6`  | 1.21.6    | Fabric | 21   | **Active**    |
| `1.21.7`  | 1.21.7    | Fabric | 21   | **Active**    |
| `1.21.8`  | 1.21.8    | Fabric | 21   | **Active**    |
| `1.21.9`  | 1.21.9    | Fabric | 21   | **Active**    |
| `1.21.10` | 1.21.10   | Fabric | 21   | **Active**    |
| `1.21.11` | 1.21.11   | Fabric | 21   | **Active**    |
| `26.1`    | 26.1      | Fabric | 21   | **Active**    |
| `26.1.1`  | 26.1.1    | Fabric | 21   | **Active**    |
| `26.1.2`  | 26.1.2    | Fabric | 21   | **Active**    |

All versions target **Fabric only** (Forge removed from new work).

