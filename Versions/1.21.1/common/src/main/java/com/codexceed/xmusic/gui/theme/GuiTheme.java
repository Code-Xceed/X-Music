package com.codexceed.xmusic.gui.theme;

public final class GuiTheme {
    private GuiTheme() {}

    // ── Backgrounds ─────────────────────────────────────────────────────
    public static final int OVERLAY       = 0x80000000;
    public static final int FRAME         = 0xFF16171E; // Slate black
    public static final int FRAME_EDGE    = 0xFF08080C;
    public static final int PANEL         = 0xFF20222B; // Dark slate panel
    public static final int PANEL_DARK    = 0xFF121319; // Inset slate dark
    public static final int PANEL_HOVER   = 0xFF2B2E3A; // Highlight hover slate
    public static final int PANEL_ACTIVE  = 0xFF192530; // Active selection slate-cyan

    // ── Gradient Backgrounds (for depth layers) ─────────────────────────
    public static final int FRAME_TOP     = 0xFF1F212B;   // gradient top
    public static final int FRAME_BOTTOM  = 0xFF0F1014;   // gradient bottom
    public static final int PANEL_GRAD_TOP    = 0xFF282B36;
    public static final int PANEL_GRAD_BOTTOM = 0xFF1A1C23;

    // ── Accents ─────────────────────────────────────────────────────────
    public static final int ACCENT        = 0xFF00E5FF; // Electric neon cyan
    public static final int ACCENT_DARK   = 0xFF00A0B2;
    public static final int ACCENT_BRIGHT = 0xFF80F3FF; // Brighter neon hover
    public static final int SPOTIFY_GREEN = 0xFF1DB954;

    public static final int DANGER        = 0xFFFF3B4B;

    // ── Bevel depth (dark-mode: lighter = highlight, darker = shadow) ──
    public static final int BEVEL_HIGHLIGHT      = 0xFF4A4A4A; // raised top-left
    public static final int BEVEL_SHADOW         = 0xFF111111; // raised bottom-right
    public static final int BEVEL_HIGHLIGHT_HOVER= 0xFF5A5A5A; // brighter on hover
    public static final int BEVEL_HIGHLIGHT_INSET= 0xFF151515; // inset top-left (darker)
    public static final int BEVEL_SHADOW_INSET   = 0xFF3A3A3A; // inset bottom-right (lighter)

    // ── Inventory slot ──────────────────────────────────────────────────
    public static final int SLOT_BG        = 0xFF131313;
    public static final int SLOT_HIGHLIGHT = 0xFF3A3A3A;

    // ── Glow & Tooltip ─────────────────────────────────────────────────
    public static final int GLOW_ACCENT    = 0x403BF0FF; // semi-transparent accent glow
    public static final int GLOW_ACCENT_MID = 0x253BF0FF; // mid-layer glow
    public static final int GLOW_ACCENT_SOFT= 0x153BF0FF; // outer soft glow
    public static final int GLOW_ACCENT_DEEP= 0x0A3BF0FF; // deepest outer halo
    public static final int GLOW_DIM       = 0x201A1A1A; // subtle dark glow
    public static final int TOOLTIP_BG     = 0xE0101010; // tooltip background
    public static final int TOOLTIP_BORDER = 0xFF505050; // tooltip border

    // ── Bloom / Atmosphere ──────────────────────────────────────────────
    public static final int BLOOM_WHITE    = 0x08FFFFFF; // ultra-subtle white bloom
    public static final int BLOOM_ACCENT   = 0x103BF0FF; // soft accent atmospheric bloom
    public static final int VIGNETTE_EDGE  = 0x30000000; // vignette darkening at frame edges
    public static final int DEPTH_SHADOW   = 0x40000000; // inter-panel depth shadow

    // ── Hover animation ────────────────────────────────────────────────
    public static final int HOVER_TICKS    = 4;          // ticks to reach full hover (legacy)

    // ── Hover colors for smooth interpolation ──────────────────────────
    public static final int HOVER_BG_OFF   = 0x00000000; // transparent when not hovered
    public static final int HOVER_BG_ON    = 0x18FFFFFF; // subtle white tint when hovered
    public static final int HOVER_GLOW     = 0x183BF0FF; // subtle accent glow on hover

    // ── Text ─────────────────────────────────────────────────────────────
    public static final int TEXT       = 0xFFE0E0E0;
    public static final int TEXT_SOFT  = 0xFFB0B0B0;
    public static final int TEXT_MUTED = 0xFF787878;
    public static final int DISABLED   = 0xFF505050;

    // ── Layout ───────────────────────────────────────────────────────────
    public static final int PADDING    = 5;
    public static final int SMALL_GAP  = 3;
    public static final int ROW_HEIGHT = 20;

    // ── Home Page Cards ──────────────────────────────────────────────────
    public static final int CARD_BG        = 0xFF2E2E2E;
    public static final int CARD_HOVER     = 0xFF3E3E3E;
    public static final int CARD_BORDER    = 0xFF404040;
    public static final int SECTION_HEADER = 0xFFE0E0E0;
    public static final int QUICK_TILE_BG  = 0xFF333333;
    public static final int QUICK_TILE_HOVER = 0xFF444444;

    // ── Intro/Outro Animation ───────────────────────────────────────────
    public static final long INTRO_DURATION_MS = 350;     // total intro animation length
    public static final long OUTRO_DURATION_MS = 250;     // total outro animation length
    public static final float STAGGER_OVERLAP  = 0.65f;   // cascade overlap (0→1)
}
