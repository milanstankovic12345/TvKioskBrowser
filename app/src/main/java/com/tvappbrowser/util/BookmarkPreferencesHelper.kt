package com.tvappbrowser.util

import android.content.Context
import android.content.SharedPreferences
import com.tvappbrowser.model.Bookmark
import org.json.JSONArray
import org.json.JSONObject

class BookmarkPreferencesHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tv_browser_prefs", Context.MODE_PRIVATE)

    private var cachedBookmarks: List<Bookmark>? = null

    fun getBookmarks(): List<Bookmark> {
        if (cachedBookmarks != null) {
            return cachedBookmarks!!
        }
        val bookmarksJson = prefs.getString("bookmarks", "[]") ?: "[]"
        val list = mutableListOf<Bookmark>()
        try {
            val jsonArray = JSONArray(bookmarksJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(Bookmark(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    url = obj.getString("url")
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        cachedBookmarks = list
        return list
    }

    private fun saveBookmarks(list: List<Bookmark>) {
        cachedBookmarks = list
        val jsonArray = JSONArray()
        list.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("name", it.name)
            obj.put("url", it.url)
            jsonArray.put(obj)
        }
        prefs.edit().putString("bookmarks", jsonArray.toString()).apply()
    }

    fun isBookmarked(url: String): Boolean {
        return getBookmarks().any { it.url == url }
    }

    fun getBookmarkByUrl(url: String): Bookmark? {
        return getBookmarks().find { it.url == url }
    }

    fun addBookmark(bookmark: Bookmark) {
        val currentList = getBookmarks().toMutableList()
        currentList.add(bookmark)
        saveBookmarks(currentList)
    }

    fun updateBookmark(bookmark: Bookmark) {
        val currentList = getBookmarks().toMutableList()
        val index = currentList.indexOfFirst { it.id == bookmark.id }
        if (index != -1) {
            currentList[index] = bookmark
            saveBookmarks(currentList)
        }
    }

    fun deleteBookmark(id: String) {
        val currentList = getBookmarks().toMutableList()
        currentList.removeAll { it.id == id }
        saveBookmarks(currentList)
    }
}
