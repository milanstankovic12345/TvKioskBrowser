package com.tvappbrowser.model

data class AppSettings(
    val url: String = "https://www.google.com",
    val refreshIntervalSec: Int = 300,
    val startOnBoot: Boolean = true,
    val kioskMode: Boolean = false,
    val javascriptEnabled: Boolean = true,
    val hardwareAccel: Boolean = true,
    val clearCache: Boolean = false,
    val zoomLevel: Int = 100,
    val pinCode: String = "1234",
    val bookmarks: List<Bookmark> = emptyList()
)
