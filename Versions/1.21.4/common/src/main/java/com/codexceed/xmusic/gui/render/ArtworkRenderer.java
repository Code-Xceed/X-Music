package com.codexceed.xmusic.gui.render;

import com.codexceed.xmusic.XMusic;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.*;
import java.net.HttpURLConnection;
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
    private static final Map<String, ResourceLocation> TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> DOWNLOADING = new ConcurrentHashMap<>();
    private static int textureCounter = 0;

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
    public static void renderArtwork(GuiGraphics g, String artworkUrl, int x, int y, int size) {
        if (artworkUrl == null || artworkUrl.isEmpty()) {
            renderPlaceholder(g, x, y, size);
            return;
        }

        ResourceLocation loc = TEXTURE_CACHE.get(artworkUrl);
        if (loc != null) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            g.blit(RenderType::guiTextured, loc, x, y, 0f, 0f, size, size, size, size);
            return;
        }

        // Not loaded yet — trigger async download if not already in progress
        if (!DOWNLOADING.containsKey(artworkUrl)) {
            DOWNLOADING.put(artworkUrl, true);
            EXECUTOR.submit(() -> downloadAndLoad(artworkUrl));
        }

        renderPlaceholder(g, x, y, size);
    }

    /** Render a dark placeholder with a music note icon. */
    public static void renderPlaceholder(GuiGraphics g, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, 0xFF1A1A1A);
        // Simple note icon using text
        var font = Minecraft.getInstance().font;
        String note = "\u266A";
        int nx = x + (size - font.width(note)) / 2;
        int ny = y + (size - 8) / 2;
        g.drawString(font, note, nx, ny, 0xFF505050, false);
    }

    /** Download artwork to cache, then upload as MC texture on render thread. */
    private static void downloadAndLoad(String artworkUrl) {
        try {
            String hash = Integer.toHexString(artworkUrl.hashCode());
            Path cachedFile = CACHE_DIR != null ? CACHE_DIR.resolve(hash + ".png") : null;

            // Download if not cached
            if (cachedFile == null || !Files.exists(cachedFile)) {
                if (cachedFile != null) downloadToFile(artworkUrl, cachedFile);
            }

            // Load on render thread
            if (cachedFile != null && Files.exists(cachedFile)) {
                Minecraft.getInstance().execute(() -> {
                    try {
                        loadTexture(artworkUrl, cachedFile);
                    } catch (Exception e) {
                        XMusic.LOGGER.debug("Failed to load artwork texture for {}", artworkUrl, e);
                    } finally {
                        DOWNLOADING.remove(artworkUrl);
                    }
                });
            } else {
                DOWNLOADING.remove(artworkUrl);
            }
        } catch (Exception e) {
            XMusic.LOGGER.debug("Failed to download artwork: {}", artworkUrl, e);
            DOWNLOADING.remove(artworkUrl);
        }
    }

    private static void downloadToFile(String urlStr, Path target) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "CodeX-Music-Player/1.0");
        try (InputStream in = conn.getInputStream(); OutputStream out = Files.newOutputStream(target)) {
            in.transferTo(out);
        } finally {
            conn.disconnect();
        }
    }

    private static void loadTexture(String artworkUrl, Path file) throws IOException {
        try (InputStream is = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(is);
            int texId = textureCounter++;
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("xmusic", "art_" + texId);
            Minecraft.getInstance().getTextureManager().register(loc, texture);
            TEXTURE_CACHE.put(artworkUrl, loc);
            XMusic.LOGGER.debug("Artwork texture loaded: {}", artworkUrl);
        }
    }

    /** Clear all cached textures (call on resource reload). */
    public static void clearCache() {
        for (ResourceLocation loc : TEXTURE_CACHE.values()) {
            Minecraft.getInstance().getTextureManager().release(loc);
        }
        TEXTURE_CACHE.clear();
    }
}
