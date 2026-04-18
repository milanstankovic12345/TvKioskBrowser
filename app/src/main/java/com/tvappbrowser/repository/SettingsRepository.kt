package com.tvappbrowser.repository

import android.content.Context
import android.content.SharedPreferences
import com.tvappbrowser.model.AppSettings
import org.json.JSONObject
import java.io.File

class SettingsRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("tv_browser_prefs", Context.MODE_PRIVATE)

    fun getSettings(): AppSettings {
        // First try to load from remote config file (overrides local prefs)
        val remoteConfig = loadRemoteConfig()

        val bookmarksJson = prefs.getString("bookmarks", "[]") ?: "[]"
        val bookmarksList = mutableListOf<com.tvappbrowser.model.Bookmark>()
        try {
            val jsonArray = org.json.JSONArray(bookmarksJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                bookmarksList.add(com.tvappbrowser.model.Bookmark(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    url = obj.getString("url")
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return AppSettings(
            url = remoteConfig?.optString("url") ?: prefs.getString("url", "https://www.google.com") ?: "https://www.google.com",
            refreshIntervalSec = remoteConfig?.optInt("refreshInterval") ?: prefs.getInt("refreshIntervalSec", 300),
            startOnBoot = remoteConfig?.optBoolean("startOnBoot") ?: prefs.getBoolean("startOnBoot", true),
            kioskMode = remoteConfig?.optBoolean("kioskMode") ?: prefs.getBoolean("kioskMode", false),
            javascriptEnabled = remoteConfig?.optBoolean("javascriptEnabled") ?: prefs.getBoolean("javascriptEnabled", true),
            hardwareAccel = remoteConfig?.optBoolean("hardwareAccel") ?: prefs.getBoolean("hardwareAccel", true),
            clearCache = remoteConfig?.optBoolean("clearCache") ?: prefs.getBoolean("clearCache", false),
            zoomLevel = remoteConfig?.optInt("zoomLevel") ?: prefs.getInt("zoomLevel", 100),
            pinCode = prefs.getString("pinCode", "1234") ?: "1234", // PIN is strictly local
            bookmarks = bookmarksList
        )
    }

    fun saveSettings(settings: AppSettings) {
        val jsonArray = org.json.JSONArray()
        settings.bookmarks.forEach {
            val obj = org.json.JSONObject()
            obj.put("id", it.id)
            obj.put("name", it.name)
            obj.put("url", it.url)
            jsonArray.put(obj)
        }

        prefs.edit().apply {
            putString("url", settings.url)
            putInt("refreshIntervalSec", settings.refreshIntervalSec)
            putBoolean("startOnBoot", settings.startOnBoot)
            putBoolean("kioskMode", settings.kioskMode)
            putBoolean("javascriptEnabled", settings.javascriptEnabled)
            putBoolean("hardwareAccel", settings.hardwareAccel)
            putBoolean("clearCache", settings.clearCache)
            putInt("zoomLevel", settings.zoomLevel)
            putString("pinCode", settings.pinCode)
            putString("bookmarks", jsonArray.toString())
            apply()
        }
    }

    private fun loadRemoteConfig(): JSONObject? {
        try {
            val file = File("/sdcard/TVAppBrowser/config.json")
            if (file.exists() && file.canRead()) {
                val jsonString = file.readText()
                return JSONObject(jsonString)
            }
        } catch (e: Exception) {
            // Silently fail if not readable (missing permissions or non-existent file)
            e.printStackTrace()
        }
        return null
    }
}
