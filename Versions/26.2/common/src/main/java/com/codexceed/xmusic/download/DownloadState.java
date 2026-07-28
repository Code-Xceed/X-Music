package com.codexceed.xmusic.download;

/**
 * Download state for a track.
 */
public enum DownloadState {
    NONE,           // Not downloading, not downloaded
    DOWNLOADING,    // Currently downloading
    COMPLETED,      // Download finished successfully
    FAILED,         // Download failed
    CANCELLED       // Download was cancelled by user
}
