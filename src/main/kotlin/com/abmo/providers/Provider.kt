package com.abmo.providers

/**
 * Interface representing a video provider.
 *
 * This interface defines the contract for obtaining a video ID from a given URL.
 */
interface Provider {
    /**
     * Retrieves the video ID from the specified URL.
     *
     * @param url The URL of the video.
     * @return The video ID as a String, or null if not found.
     */
    fun getVideoID(url: String): String?

    fun getDownloadTargets(url: String): List<DownloadTarget> {
        val videoId = getVideoID(url) ?: return emptyList()
        return listOf(DownloadTarget(videoId))
    }
}
