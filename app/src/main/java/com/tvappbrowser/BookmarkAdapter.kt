package com.tvappbrowser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tvappbrowser.model.Bookmark

class BookmarkAdapter(
    private val bookmarks: List<Bookmark>,
    private val onSiteClicked: (Bookmark) -> Unit,
    private val onEditClicked: (Bookmark) -> Unit,
    private val onDeleteClicked: (Bookmark) -> Unit
) : RecyclerView.Adapter<BookmarkAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rootCard: View = view.findViewById(R.id.rootCard)
        val textSiteName: TextView = view.findViewById(R.id.textSiteName)
        val textSiteUrl: TextView = view.findViewById(R.id.textSiteUrl)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recommended_site, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bookmark = bookmarks[position]
        holder.textSiteName.text = bookmark.name
        holder.textSiteUrl.text = bookmark.url
        
        holder.rootCard.setOnClickListener {
            onSiteClicked(bookmark)
        }
        
        holder.btnEdit.setOnClickListener {
            onEditClicked(bookmark)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClicked(bookmark)
        }
    }

    override fun getItemCount() = bookmarks.size
}
