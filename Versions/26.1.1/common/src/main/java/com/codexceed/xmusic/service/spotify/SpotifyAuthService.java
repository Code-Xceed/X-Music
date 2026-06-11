package com.codexceed.xmusic.service.spotify;

/**
 * Stub â€” kept for ServiceManager wiring.
 * Spotify API is no longer used; search uses YouTube backend directly.
 */
public final class SpotifyAuthService {

    public SpotifyAuthService() {}

    public boolean isAuthenticated() { return true; }
    public String getAccessToken() { return ""; }
}
