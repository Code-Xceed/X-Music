package com.codexceed.xmusic.gui.theme;

public final class GuiTheme {
    private GuiTheme() {}

    // ── Backgrounds ─────────────────────────────────────────────────────
    public static final int OVERLAY       = 0x80000000;
    public static final int FRAME         = 0xFF1E1E1E;
    public static final int FRAME_EDGE    = 0xFF0A0A0A;
    public static final int PANEL         = 0xFF2A2A2A;
    public static final int PANEL_DARK    = 0xFF1A1A1A;
    public static final int PANEL_HOVER   = 0xFF3A3A3A;
    public static final int PANEL_ACTIVE  = 0xFF2A1A1A;

    // ── Accents ─────────────────────────────────────────────────────────
    public static final int ACCENT        = 0xFF3BF0FF; // MC cyan
    public static final int ACCENT_DARK   = 0xFF2A9DB5;
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
    public static final int GLOW_DIM       = 0x201A1A1A; // subtle dark glow
    public static final int TOOLTIP_BG     = 0xE0101010; // tooltip background
    public static final int TOOLTIP_BORDER = 0xFF505050; // tooltip border

    // ── Hover animation ────────────────────────────────────────────────
    public static final int HOVER_TICKS    = 4;          // ticks to reach full hover

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
}
