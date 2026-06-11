package com.codexceed.xmusic.config;

import com.codexceed.xmusic.XMusic;
import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Bundled product-level defaults.
 */
public final class ProductDefaults {
    private static final String RESOURCE_PATH = "/xmusic-product.json";
    private static final Gson GSON = new Gson();
    private static final ProductDefaults INSTANCE = load();

    private String youtubeApiKey = "";
    private String distributionChannel = "development";

    private ProductDefaults() {}

    public static ProductDefaults get() {
        return INSTANCE;
    }

    public String getYoutubeApiKey() {
        return sanitize(youtubeApiKey);
    }

    public String getDistributionChannel() {
        String value = sanitize(distributionChannel);
        return value.isEmpty() ? "development" : value;
    }

    private static ProductDefaults load() {
        try (InputStream stream = ProductDefaults.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                XMusic.LOGGER.warn("Bundled product defaults '{}' were not found.", RESOURCE_PATH);
                return new ProductDefaults();
            }

            ProductDefaults defaults = GSON.fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8),
                    ProductDefaults.class);
            return defaults != null ? defaults : new ProductDefaults();
        } catch (Exception e) {
            XMusic.LOGGER.warn("Failed to load bundled product defaults.", e);
            return new ProductDefaults();
        }
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if ("YOUR_YOUTUBE_API_KEY".equals(trimmed)) {
            return "";
        }
        return trimmed;
    }
}
