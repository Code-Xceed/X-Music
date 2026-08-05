package com.codexceed.xmusic.gui.render;

import com.codexceed.xmusic.XMusic;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import com.codexceed.xmusic.source.TrackRef;
import com.codexceed.xmusic.download.DownloadManager;
import com.codexceed.xmusic.download.DownloadState;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchronous album-art downloader and renderer.
 * Downloads artwork URLs to a disk cache, loads them as MC textures,
 * and provides a simple render method for GUI components.
 */
public final class ArtworkRenderer {

    private static final Path CACHE_DIR;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "xmusic-artwork");
        t.setDaemon(true);
        return t;
    });
    private static final Map<String, ResourceLocation> TEXTURE_CACHE = new java.util.LinkedHashMap<String, ResourceLocation>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ResourceLocation> eldest) {
            if (size() > 64) {
                Minecraft.getInstance().execute(() -> {
                    Minecraft.getInstance().getTextureManager().release(eldest.getValue());
                });
                return true;
            }
            return false;
        }
    };
    private static final Map<String, Boolean> DOWNLOADING = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicInteger textureCounter = new java.util.concurrent.atomic.AtomicInteger(0);

    static {
        Path dir = null;
        try {
            dir = XMusic.getPlatform().getConfigDir().resolve("xmusic").resolve("artwork");
            Files.createDirectories(dir);
        } catch (Exception e) {
            XMusic.LOGGER.error("Failed to create artwork cache dir", e);
        }
        CACHE_DIR = dir;
    }

    private ArtworkRenderer() {}

    /**
     * Render artwork for the given URL at the specified position and size.
     * If the artwork is not yet loaded, renders a placeholder.
     * Thread-safe: downloads happen asynchronously.
     */
    public static void renderArtwork(GuiGraphics g, String artworkUrl, int x, int y, int w, int h) {
        renderArtwork(g, artworkUrl, null, x, y, w, h, 1.0f);
    }

    public static void renderArtwork(GuiGraphics g, TrackRef track, int x, int y, int w, int h) {
        renderArtwork(g, track != null ? track.getArtworkUrl() : null, track, x, y, w, h, 1.0f);
    }

    public static void renderArtwork(GuiGraphics g, TrackRef track, int x, int y, int w, int h, float alpha) {
        renderArtwork(g, track != null ? track.getArtworkUrl() : null, track, x, y, w, h, alpha);
    }

    public static void renderArtwork(GuiGraphics g, String artworkUrl, TrackRef track, int x, int y, int w, int h) {
        renderArtwork(g, artworkUrl, track, x, y, w, h, 1.0f);
    }

    public static void renderArtwork(GuiGraphics g, String artworkUrl, TrackRef track, int x, int y, int w, int h, float alpha) {
        if (artworkUrl == null || artworkUrl.isEmpty()) {
            renderPlaceholder(g, track, x, y, w, h, alpha);
            return;
        }

        ResourceLocation loc = TEXTURE_CACHE.get(artworkUrl);
        if (loc != null) {
            var texture = Minecraft.getInstance().getTextureManager().getTexture(loc);
            if (texture != null) {
                texture.setFilter(true, false);
            }
            
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, loc, x, y, 0.0f, 0.0f, w, h, w, h);
            
            // Draw a subtle border overlay to frame the artwork
            GuiRender.outline(g, x, y, w, h, (int)(0x30 * alpha) << 24 | 0xFFFFFF);
            return;
        }

        // Not loaded yet Ã¢â‚¬â€ trigger async download if not already in progress
        if (!DOWNLOADING.containsKey(artworkUrl)) {
            DOWNLOADING.put(artworkUrl, true);
            EXECUTOR.submit(() -> downloadAndLoad(artworkUrl));
        }

        renderPlaceholder(g, track, x, y, w, h, alpha);
    }

    /** Render a dark placeholder with a music note icon. */
    public static void renderPlaceholder(GuiGraphics g, int x, int y, int w, int h) {
        renderPlaceholder(g, null, x, y, w, h, 1.0f);
    }

    public static void renderPlaceholder(GuiGraphics g, TrackRef track, int x, int y, int w, int h) {
        renderPlaceholder(g, track, x, y, w, h, 1.0f);
    }

    /** Render a beautiful, custom-typed placeholder depending on track metadata! */
    public static void renderPlaceholder(GuiGraphics g, TrackRef track, int x, int y, int w, int h, float alpha) {
        int bgCol = ((int)(0xFF * alpha) << 24) | 0x0F1014;
        g.fill(x, y, x + w, y + h, bgCol);
        
        String symbol = "Ã¢â„¢Â«";
        int color = 0x00E5FF; // Default accent cyan
        
        if (track != null) {
            boolean isDownloaded = DownloadManager.getInstance().getState(track) == DownloadState.COMPLETED;
            boolean isLocal = "local".equals(track.getSourceId());
            if (isDownloaded) {
                symbol = "Ã¢â„¢Â«";
                color = 0x55FF55; // lime green for downloaded
            } else if (isLocal) {
                symbol = "Ã¢â„¢Âª";
                color = 0xFFB74D; // warm orange/wood for local
            } else {
                symbol = "Ã¢â„¢Â«";
                color = 0xFF5555; // red for streamed/YouTube
            }
        }

        // Draw a subtle colored soft glow inside
        int glowColor = (int)(0x12 * alpha) << 24 | (color & 0xFFFFFF);
        g.fill(x, y, x + w, y + h, glowColor);

        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MUSIC_DISC_MALL);

        if (track != null) {
            boolean isDownloaded = DownloadManager.getInstance().getState(track) == DownloadState.COMPLETED;
            boolean isLocal = "local".equals(track.getSourceId());
            if (isDownloaded) {
                stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MUSIC_DISC_FAR); // green disc
            } else if (isLocal) {
                stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MUSIC_DISC_BLOCKS); // orange disc
            } else {
                stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MUSIC_DISC_CHIRP); // red disc
            }
        }

        // Render the Minecraft item icon
        IconRenderer.itemIcon(g, stack, x, y, w, h);

        // Draw a glowing accent outline
        GuiRender.outline(g, x, y, w, h, ((int)(0x35 * alpha) << 24) | (color & 0x00FFFFFF));
    }

    private static boolean isValidArtworkUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String lower = url.toLowerCase();
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    /** Download artwork to cache, then upload as MC texture on render thread. */
    private static void downloadAndLoad(String artworkUrl) {
        if (!isValidArtworkUrl(artworkUrl)) {
            DOWNLOADING.remove(artworkUrl);
            return;
        }
        try {
            String hash = Integer.toHexString(artworkUrl.hashCode());
            Path cachedFile = CACHE_DIR != null ? CACHE_DIR.resolve(hash + "_raw.png") : null;

            // Download if not cached
            if (cachedFile == null || !Files.exists(cachedFile)) {
                if (cachedFile != null) {
                    XMusic.LOGGER.info("Downloading artwork to cache: {}", artworkUrl);
                    downloadToFile(artworkUrl, cachedFile);
                }
            }

            // Load on render thread
            if (cachedFile != null && Files.exists(cachedFile)) {
                Minecraft.getInstance().execute(() -> {
                    try {
                        loadTexture(artworkUrl, cachedFile);
                    } catch (Exception e) {
                        XMusic.LOGGER.error("Failed to load artwork texture for: " + artworkUrl, e);
                    } finally {
                        DOWNLOADING.remove(artworkUrl);
                    }
                });
            } else {
                DOWNLOADING.remove(artworkUrl);
            }
        } catch (Exception e) {
            XMusic.LOGGER.error("Failed to download artwork: " + artworkUrl, e);
            DOWNLOADING.remove(artworkUrl);
        }
    }

    private static void downloadToFile(String urlStr, Path target) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "CodeX-Music-Player/1.0");
        try (InputStream in = conn.getInputStream()) {
            BufferedImage image = ImageIO.read(in);
            if (image != null) {
                try (OutputStream out = Files.newOutputStream(target)) {
                    ImageIO.write(image, "png", out);
                }
            } else {
                throw new IOException("Failed to decode image from " + urlStr);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static void loadTexture(String artworkUrl, Path file) throws IOException {
        InputStream is = Files.newInputStream(file);
        NativeImage image = NativeImage.read(is);
        is.close();
        
        Minecraft.getInstance().execute(() -> {
            int texId = textureCounter.getAndIncrement();
            DynamicTexture texture = new DynamicTexture(() -> "xmusic_art_" + texId, image);
            texture.upload();
            texture.setFilter(true, false);
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("xmusic", "art_" + texId);
            Minecraft.getInstance().getTextureManager().register(loc, texture);
            TEXTURE_CACHE.put(artworkUrl, loc);
            XMusic.LOGGER.info("Artwork texture loaded and registered on render thread: {}", artworkUrl);
        });
    }

    /** Clear all cached textures (call on resource reload). */
    public static void clearCache() {
        for (ResourceLocation loc : TEXTURE_CACHE.values()) {
            Minecraft.getInstance().getTextureManager().release(loc);
        }
        TEXTURE_CACHE.clear();
    }
}

