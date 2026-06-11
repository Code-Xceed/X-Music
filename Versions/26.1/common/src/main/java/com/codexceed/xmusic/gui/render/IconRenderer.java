package com.codexceed.xmusic.gui.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Hybrid icon renderer:
 *  â€¢ Minecraft ItemStack icons â€” pixel-perfect MC art for static icons
 *  â€¢ Scaled unicode â€” for icons that need per-state color control
 * Every icon auto-fits its parent container.
 */
public final class IconRenderer {
    private IconRenderer() {}

    /** Functional interface for icon renderers, used by ToolbarButton. */
    @FunctionalInterface
    public interface IconFunc {
        void render(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int color);
    }

    private static final float FILL = 0.75f;

    // â”€â”€ Pre-built ItemStacks (no per-frame allocation) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
    private static final ItemStack STK_CHAIN      = new ItemStack(Items.IRON_CHAIN);
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

    // â”€â”€ Sidebar Icons (MC items) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static void home(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_BED, x, y, w, h);
    }

    public static void search(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_COMPASS, x, y, w, h);
    }

    public static void library(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_BOOK, x, y, w, h);
    }

    public static void groups(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_HEAD, x, y, w, h);
    }

    public static void downloads(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_HOPPER, x, y, w, h);
    }

    public static void settings(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_COMPARATOR, x, y, w, h);
    }

    // â”€â”€ Playback Icons (unicode â€” need color control for active/hover) â”€â”€

    public static void play(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u25B6", x, y, w, h, c);
    }

    public static void pause(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u275A\u275A", x, y, w, h, c);
    }

    public static void prev(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u25C0", x, y, w, h, c);
    }

    public static void next(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u25B6", x, y, w, h, c);
    }

    public static void skipBack(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u23EE", x, y, w, h, c);
    }

    public static void skipForward(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u23ED", x, y, w, h, c);
    }

    // â”€â”€ PlayerBar Right Icons (MC items) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static void volume(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_NOTE_BLOCK, x, y, w, h);
    }

    public static void loop(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_REPEATER, x, y, w, h);
    }

    // â”€â”€ Search Tab Icons (MC items where possible) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static void playAll(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u25B6", x, y, w, h, c);
    }

    public static void shuffle(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u21C4", x, y, w, h, c);
    }

    public static void durationFilter(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_CLOCK, x, y, w, h);
    }

    public static void history(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_SPYGLASS, x, y, w, h);
    }

    public static void recent(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_DISC, x, y, w, h);
    }

    public static void autoPlay(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_LEVER, x, y, w, h);
    }

    // â”€â”€ Track Row Icons (unicode heart â€” needs color; MC item download) â”€â”€

    public static void heart(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u2665", x, y, w, h, c);
    }

    public static void heartFilled(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u2764", x, y, w, h, c);
    }

    public static void download(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_CHEST, x, y, w, h);
    }

    public static void checkmark(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u2713", x, y, w, h, c);
    }

    public static void cross(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u2717", x, y, w, h, c);
    }

    // â”€â”€ Utility Icons (MC items) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static void clear(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_BARRIER, x, y, w, h);
    }

    public static void url(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_CHAIN, x, y, w, h);
    }

    public static void paste(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_PAPER, x, y, w, h);
    }

    public static void copy(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_WRITABLE, x, y, w, h);
    }

    public static void musicNote(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_NOTE_BLOCK, x, y, w, h);
    }

    public static void nowPlaying(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_DISC_ALT, x, y, w, h);
    }

    // â”€â”€ Library Tab Icons â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static void chevronRight(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u25B8", x, y, w, h, c);
    }

    public static void plus(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "+", x, y, w, h, c);
    }

    public static void backArrow(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u25C0", x, y, w, h, c);
    }

    public static void album(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_DISC, x, y, w, h);
    }

    public static void source(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_COMPASS, x, y, w, h);
    }

    public static void playlistBook(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_KNOWLEDGE, x, y, w, h);
    }

    public static void mapIcon(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_MAP, x, y, w, h);
    }

    public static void delete(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_BARRIER, x, y, w, h);
    }

    // â”€â”€ Home Page Icons â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static void fire(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_BLAZE_POWDER, x, y, w, h);
    }

    public static void clockRecent(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_CLOCK, x, y, w, h);
    }

    public static void star(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_STAR, x, y, w, h);
    }

    // â”€â”€ Category Disc Icons (different colored MC discs) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static void discMostPlayed(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_DISC_CHIRP, x, y, w, h);    // red disc â€” hot/fire
    }

    public static void discRecent(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_DISC_BLOCKS, x, y, w, h);   // orange disc â€” recent/warm
    }

    public static void discAlbums(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_DISC_MELLOHI, x, y, w, h);  // pink disc â€” albums
    }

    public static void discArtists(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_DISC_STAL, x, y, w, h);     // brown disc â€” artists
    }

    public static void discPlaylists(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_DISC_WAIT, x, y, w, h);     // purple disc â€” playlists
    }

    public static void folder(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, STK_CHEST, x, y, w, h);
    }

    public static void rescan(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u21BB", x, y, w, h, c);
    }

    // â”€â”€ Generic helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static void symbol(GuiGraphicsExtractor g, Font f, String sym, int x, int y, int w, int h, int c) {
        fit(g, f, sym, x, y, w, h, c);
    }

    public static void itemIcon(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int w, int h) {
        item(g, stack, x, y, w, h);
    }

    // â”€â”€ Core: MC Item Rendering â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Renders a Minecraft ItemStack scaled and centered to fill
     * FILL% of the given area. Items render at 16x16 base size.
     */
    private static void item(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int areaW, int areaH) {
        float scale = Math.min(areaW, areaH) * FILL / 16.0f;
        float drawnW = 16.0f * scale;
        float drawnH = 16.0f * scale;
        float offX = (areaW - drawnW) / 2.0f;
        float offY = (areaH - drawnH) / 2.0f;
        g.pose().pushMatrix();
        g.pose().translate(x + offX, y + offY);
        g.pose().scale(scale, scale);
        g.item(stack, 0, 0);
        g.pose().popMatrix();
    }

    // â”€â”€ Core: Scaled Unicode Rendering â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Auto-scales a unicode symbol to fill FILL% of (areaW x areaH),
     * then centers it. Used for icons that need color control.
     */
    private static void fit(GuiGraphicsExtractor g, Font f, String symbol, int x, int y, int areaW, int areaH, int color) {
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

    private static void drawScaled(GuiGraphicsExtractor g, Font f, String text, float scale, float x, float y, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(f, text, 0, 0, color, false);
        g.pose().popMatrix();
    }
}
