package com.codexceed.xmusic.gui.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
        void render(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int color);
    }

    private static final float FILL = 0.75f;

    private static final java.util.Map<net.minecraft.world.level.ItemLike, ItemStack> STACK_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static ItemStack getStack(net.minecraft.world.level.ItemLike item) {
        if (item == null) return null;
        ItemStack existing = STACK_CACHE.get(item);
        if (existing != null) return existing;

        try {
            net.minecraft.world.item.Item it = item.asItem();
            if (it != null) {
                net.minecraft.core.Holder.Reference<net.minecraft.world.item.Item> holder = it.builtInRegistryHolder();
                if (holder != null && !holder.areComponentsBound()) {
                    return null;
                }
            }
            ItemStack stack = new ItemStack(item);
            STACK_CACHE.put(item, stack);
            return stack;
        } catch (Throwable t) {
            return null;
        }
    }

    // ── Sidebar Icons (MC items) ────────────────────────────────────────

    public static void home(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.RED_BED, x, y, w, h);
    }

    public static void search(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.COMPASS, x, y, w, h);
    }

    public static void library(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.BOOK, x, y, w, h);
    }

    public static void groups(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.PLAYER_HEAD, x, y, w, h);
    }

    public static void downloads(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.HOPPER, x, y, w, h);
    }

    public static void settings(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.COMPARATOR, x, y, w, h);
    }

    // ── Playback Icons (unicode — need color control for active/hover) ──

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

    // ── PlayerBar Right Icons (MC items) ────────────────────────────────

    public static void volume(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.NOTE_BLOCK, x, y, w, h);
    }

    public static void loop(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.REPEATER, x, y, w, h);
    }

    // ── Search Tab Icons (MC items where possible) ──────────────────────

    public static void playAll(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u25B6", x, y, w, h, c);
    }

    public static void shuffle(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u21C4", x, y, w, h, c);
    }

    public static void durationFilter(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.CLOCK, x, y, w, h);
    }

    public static void history(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.SPYGLASS, x, y, w, h);
    }

    public static void recent(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.MUSIC_DISC_13, x, y, w, h);
    }

    public static void autoPlay(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.LEVER, x, y, w, h);
    }

    // ── Track Row Icons (unicode heart — needs color; MC item download) ──

    public static void heart(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u2665", x, y, w, h, c);
    }

    public static void heartFilled(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u2764", x, y, w, h, c);
    }

    public static void download(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.CHEST, x, y, w, h);
    }

    public static void checkmark(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u2713", x, y, w, h, c);
    }

    public static void cross(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u2717", x, y, w, h, c);
    }

    // ── Utility Icons (MC items) ────────────────────────────────────────

    public static void clear(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.BARRIER, x, y, w, h);
    }

    public static void url(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.IRON_CHAIN, x, y, w, h);
    }

    public static void paste(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.PAPER, x, y, w, h);
    }

    public static void copy(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.WRITABLE_BOOK, x, y, w, h);
    }

    public static void musicNote(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.NOTE_BLOCK, x, y, w, h);
    }

    public static void nowPlaying(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.MUSIC_DISC_CAT, x, y, w, h);
    }

    // ── Library Tab Icons ──────────────────────────────────────────────

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
        item(g, Items.MUSIC_DISC_13, x, y, w, h);
    }

    public static void source(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.COMPASS, x, y, w, h);
    }

    public static void playlistBook(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.KNOWLEDGE_BOOK, x, y, w, h);
    }

    public static void mapIcon(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.FILLED_MAP, x, y, w, h);
    }

    public static void delete(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.BARRIER, x, y, w, h);
    }

    // ── Home Page Icons ────────────────────────────────────────────────

    public static void fire(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.BLAZE_POWDER, x, y, w, h);
    }

    public static void clockRecent(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.CLOCK, x, y, w, h);
    }

    public static void star(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.NETHER_STAR, x, y, w, h);
    }

    // ── Category Disc Icons (different colored MC discs) ────────────────

    public static void discMostPlayed(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.MUSIC_DISC_CHIRP, x, y, w, h);    // red disc — hot/fire
    }

    public static void discRecent(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.MUSIC_DISC_BLOCKS, x, y, w, h);   // orange disc — recent/warm
    }

    public static void discAlbums(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.MUSIC_DISC_MELLOHI, x, y, w, h);  // pink disc — albums
    }

    public static void discArtists(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.MUSIC_DISC_STAL, x, y, w, h);     // brown disc — artists
    }

    public static void discPlaylists(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.MUSIC_DISC_WAIT, x, y, w, h);     // purple disc — playlists
    }

    public static void folder(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        item(g, Items.CHEST, x, y, w, h);
    }

    public static void rescan(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, int c) {
        fit(g, f, "\u21BB", x, y, w, h, c);
    }

    // ── Generic helpers ─────────────────────────────────────────────────

    public static void symbol(GuiGraphicsExtractor g, Font f, String sym, int x, int y, int w, int h, int c) {
        fit(g, f, sym, x, y, w, h, c);
    }

    public static void itemIcon(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int w, int h) {
        item(g, stack, x, y, w, h);
    }

    public static void item(GuiGraphicsExtractor g, net.minecraft.world.level.ItemLike item, int x, int y, int areaW, int areaH) {
        item(g, getStack(item), x, y, areaW, areaH);
    }

    // ── Core: MC Item Rendering ─────────────────────────────────────────

    /**
     * Renders a Minecraft ItemStack scaled and centered to fill
     * FILL% of the given area. Items render at 16x16 base size.
     */
    private static void item(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int areaW, int areaH) {
        if (stack == null || stack.isEmpty()) return;
        try {
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
        } catch (Throwable ignored) {}
    }

    // ── Core: Scaled Unicode Rendering ──────────────────────────────────

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


