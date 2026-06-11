package com.codexceed.xmusic.gui.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Hybrid icon renderer:
 *  • Minecraft ItemStack icons — pixel-perfect MC art for static icons
 *  • Scaled unicode — for icons that need per-state color control
 * Every icon auto-fits its parent container.
 */
public final class IconRenderer {
    private IconRenderer() {}

    /** Functional interface for icon renderers, used by ToolbarButton. */
    @FunctionalInterface
    public interface IconFunc {
        void render(GuiGraphics g, Font f, int x, int y, int w, int h, int color);
    }

    private static final float FILL = 0.75f;

    // ── Pre-built ItemStacks (no per-frame allocation) ──────────────────

    private static final ItemStack STK_BED        = new ItemStack(Items.RED_BED);
    private static final ItemStack STK_COMPASS    = new ItemStack(Items.COMPASS);
    private static final ItemStack STK_BOOK       = new ItemStack(Items.BOOK);
    private static final ItemStack STK_HEAD       = new ItemStack(Items.PLAYER_HEAD);
    private static final ItemStack STK_HOPPER     = new ItemStack(Items.HOPPER);
    private static final ItemStack STK_COMPARATOR = new ItemStack(Items.COMPARATOR);
    private static final ItemStack STK_JUKEBOX    = new ItemStack(Items.JUKEBOX);
    private static final ItemStack STK_REPEATER   = new ItemStack(Items.REPEATER);
    private static final ItemStack STK_CLOCK      = new ItemStack(Items.CLOCK);
    private static final ItemStack STK_SPYGLASS   = new ItemStack(Items.SPYGLASS);
    private static final ItemStack STK_DISC       = new ItemStack(Items.MUSIC_DISC_13);
    private static final ItemStack STK_DISC_ALT   = new ItemStack(Items.MUSIC_DISC_CAT);
    private static final ItemStack STK_RS_TORCH   = new ItemStack(Items.REDSTONE_TORCH);
    private static final ItemStack STK_CHEST      = new ItemStack(Items.CHEST);
    private static final ItemStack STK_BARRIER    = new ItemStack(Items.BARRIER);
    private static final ItemStack STK_CHAIN      = new ItemStack(Items.CHAIN);
    private static final ItemStack STK_PAPER      = new ItemStack(Items.PAPER);
    private static final ItemStack STK_WRITABLE   = new ItemStack(Items.WRITABLE_BOOK);
    private static final ItemStack STK_LEVER      = new ItemStack(Items.LEVER);
    private static final ItemStack STK_NOTE_BLOCK = new ItemStack(Items.NOTE_BLOCK);
    private static final ItemStack STK_KNOWLEDGE   = new ItemStack(Items.KNOWLEDGE_BOOK);
    private static final ItemStack STK_MAP         = new ItemStack(Items.FILLED_MAP);
    private static final ItemStack STK_ARROW       = new ItemStack(Items.ARROW);
    private static final ItemStack STK_BLAZE_POWDER = new ItemStack(Items.BLAZE_POWDER);
    private static final ItemStack STK_STAR         = new ItemStack(Items.NETHER_STAR);

    // Different colored music discs for home page categories
    private static final ItemStack STK_DISC_13       = new ItemStack(Items.MUSIC_DISC_13);       // dark
    private static final ItemStack STK_DISC_CAT      = new ItemStack(Items.MUSIC_DISC_CAT);      // lime
    private static final ItemStack STK_DISC_BLOCKS   = new ItemStack(Items.MUSIC_DISC_BLOCKS);    // orange
    private static final ItemStack STK_DISC_CHIRP    = new ItemStack(Items.MUSIC_DISC_CHIRP);    // red
    private static final ItemStack STK_DISC_FAR      = new ItemStack(Items.MUSIC_DISC_FAR);      // green
    private static final ItemStack STK_DISC_MALL     = new ItemStack(Items.MUSIC_DISC_MALL);     // cyan
    private static final ItemStack STK_DISC_MELLOHI  = new ItemStack(Items.MUSIC_DISC_MELLOHI);  // pink
    private static final ItemStack STK_DISC_STAL     = new ItemStack(Items.MUSIC_DISC_STAL);     // brown
    private static final ItemStack STK_DISC_STRAD    = new ItemStack(Items.MUSIC_DISC_STRAD);    // white
    private static final ItemStack STK_DISC_WARD     = new ItemStack(Items.MUSIC_DISC_WARD);     // green-dark
    private static final ItemStack STK_DISC_11       = new ItemStack(Items.MUSIC_DISC_11);       // gold
    private static final ItemStack STK_DISC_WAIT     = new ItemStack(Items.MUSIC_DISC_WAIT);     // purple
    private static final ItemStack STK_DISC_PIGSTEP  = new ItemStack(Items.MUSIC_DISC_PIGSTEP);  // crimson

    // ── Sidebar Icons (MC items) ────────────────────────────────────────

    public static void home(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_BED, x, y, w, h);
    }

    public static void search(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_COMPASS, x, y, w, h);
    }

    public static void library(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_BOOK, x, y, w, h);
    }

    public static void groups(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_HEAD, x, y, w, h);
    }

    public static void downloads(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_HOPPER, x, y, w, h);
    }

    public static void settings(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_COMPARATOR, x, y, w, h);
    }

    // ── Playback Icons (unicode — need color control for active/hover) ──

    public static void play(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u25B6", x, y, w, h, c);
    }

    public static void pause(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u275A\u275A", x, y, w, h, c);
    }

    public static void prev(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u25C0", x, y, w, h, c);
    }

    public static void next(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u25B6", x, y, w, h, c);
    }

    public static void skipBack(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u23EE", x, y, w, h, c);
    }

    public static void skipForward(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u23ED", x, y, w, h, c);
    }

    // ── PlayerBar Right Icons (MC items) ────────────────────────────────

    public static void volume(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_NOTE_BLOCK, x, y, w, h);
    }

    public static void loop(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_REPEATER, x, y, w, h);
    }

    // ── Search Tab Icons (MC items where possible) ──────────────────────

    public static void playAll(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u25B6", x, y, w, h, c);
    }

    public static void shuffle(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u21C4", x, y, w, h, c);
    }

    public static void durationFilter(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_CLOCK, x, y, w, h);
    }

    public static void history(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_SPYGLASS, x, y, w, h);
    }

    public static void recent(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_DISC, x, y, w, h);
    }

    public static void autoPlay(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_LEVER, x, y, w, h);
    }

    // ── Track Row Icons (unicode heart — needs color; MC item download) ──

    public static void heart(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u2665", x, y, w, h, c);
    }

    public static void heartFilled(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u2764", x, y, w, h, c);
    }

    public static void download(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_CHEST, x, y, w, h);
    }

    public static void checkmark(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u2713", x, y, w, h, c);
    }

    public static void cross(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u2717", x, y, w, h, c);
    }

    // ── Utility Icons (MC items) ────────────────────────────────────────

    public static void clear(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_BARRIER, x, y, w, h);
    }

    public static void url(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_CHAIN, x, y, w, h);
    }

    public static void paste(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_PAPER, x, y, w, h);
    }

    public static void copy(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_WRITABLE, x, y, w, h);
    }

    public static void musicNote(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_NOTE_BLOCK, x, y, w, h);
    }

    public static void nowPlaying(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_DISC_ALT, x, y, w, h);
    }

    // ── Library Tab Icons ──────────────────────────────────────────────

    public static void chevronRight(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u25B8", x, y, w, h, c);
    }

    public static void plus(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "+", x, y, w, h, c);
    }

    public static void backArrow(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u25C0", x, y, w, h, c);
    }

    public static void album(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_DISC, x, y, w, h);
    }

    public static void source(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_COMPASS, x, y, w, h);
    }

    public static void playlistBook(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_KNOWLEDGE, x, y, w, h);
    }

    public static void mapIcon(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_MAP, x, y, w, h);
    }

    public static void delete(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_BARRIER, x, y, w, h);
    }

    // ── Home Page Icons ────────────────────────────────────────────────

    public static void fire(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_BLAZE_POWDER, x, y, w, h);
    }

    public static void clockRecent(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_CLOCK, x, y, w, h);
    }

    public static void star(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_STAR, x, y, w, h);
    }

    // ── Category Disc Icons (different colored MC discs) ────────────────

    public static void discMostPlayed(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_DISC_CHIRP, x, y, w, h);    // red disc — hot/fire
    }

    public static void discRecent(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_DISC_BLOCKS, x, y, w, h);   // orange disc — recent/warm
    }

    public static void discAlbums(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_DISC_MELLOHI, x, y, w, h);  // pink disc — albums
    }

    public static void discArtists(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_DISC_STAL, x, y, w, h);     // brown disc — artists
    }

    public static void discPlaylists(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_DISC_WAIT, x, y, w, h);     // purple disc — playlists
    }

    public static void folder(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_CHEST, x, y, w, h);
    }

    public static void rescan(GuiGraphics g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u21BB", x, y, w, h, c);
    }

    // ── Generic helpers ─────────────────────────────────────────────────

    public static void symbol(GuiGraphics g, Font f, String sym, int x, int y, int w, int h, int c) {
        fit(g, f, sym, x, y, w, h, c);
    }

    public static void itemIcon(GuiGraphics g, ItemStack stack, int x, int y, int w, int h) {
        item(g, stack, x, y, w, h);
    }

    // ── Core: MC Item Rendering ─────────────────────────────────────────

    /**
     * Renders a Minecraft ItemStack scaled and centered to fill
     * FILL% of the given area. Items render at 16x16 base size.
     */
    private static void item(GuiGraphics g, ItemStack stack, int x, int y, int areaW, int areaH) {
        float scale = Math.min(areaW, areaH) * FILL / 16.0f;
        float drawnW = 16.0f * scale;
        float drawnH = 16.0f * scale;
        float offX = (areaW - drawnW) / 2.0f;
        float offY = (areaH - drawnH) / 2.0f;
        g.pose().pushMatrix();
        g.pose().translate(x + offX, y + offY);
        g.pose().scale(scale, scale);
        g.renderItem(stack, 0, 0);
        g.pose().popMatrix();
    }

    // ── Core: Scaled Unicode Rendering ──────────────────────────────────

    /**
     * Auto-scales a unicode symbol to fill FILL% of (areaW x areaH),
     * then centers it. Used for icons that need color control.
     */
    private static void fit(GuiGraphics g, Font f, String symbol, int x, int y, int areaW, int areaH, int color) {
        float baseW = f.width(symbol);
        float baseH = 9.0f;
        float scaleX = (areaW * FILL) / baseW;
        float scaleY = (areaH * FILL) / baseH;
        float scale = Math.min(scaleX, scaleY);
        float drawnW = baseW * scale;
        float drawnH = baseH * scale;
        float offX = (areaW - drawnW) / 2.0f;
        float offY = (areaH - drawnH) / 2.0f;
        drawScaled(g, f, symbol, scale, x + offX, y + offY, color);
    }

    private static void drawScaled(GuiGraphics g, Font f, String text, float scale, float x, float y, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.drawString(f, text, 0, 0, color, false);
        g.pose().popMatrix();
    }
}
