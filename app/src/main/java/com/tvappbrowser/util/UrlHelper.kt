package com.tvappbrowser.util

object UrlHelper {
    fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("file://")) {
            return trimmed
        }
        
        if (trimmed.contains(" ") || (!trimmed.contains(".") && !trimmed.startsWith("http"))) {
            return "https://www.google.com/search?q=${android.net.Uri.encode(trimmed)}"
        } else if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") && !trimmed.startsWith("file://")) {
            return "http://$trimmed" // Default to http for local IP compatibility instead of https
        }
        
        return trimmed
    }

    fun isSafeLocalUrl(url: String): Boolean {
        if (!url.startsWith("file:///")) return false
        if (url.contains("..")) return false // Prevent directory traversal
        
        // Allow specific safe roots
        if (url.startsWith("file:///sdcard/") || 
            url.startsWith("file:///storage/") || 
            url.startsWith("file:///mnt/") || 
            url.startsWith("file:///android_asset/") ||
            url.startsWith("file:///android_res/")) {
            return true
        }
        return false
    }
}
