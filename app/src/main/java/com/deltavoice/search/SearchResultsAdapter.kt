package com.deltavoice.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deltavoice.R

class SearchResultsAdapter(
    private val onItemClick: (SearchableItem) -> Unit
) : ListAdapter<SearchableItem, SearchResultsAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val category: TextView = itemView.findViewById(R.id.item_category)
        private val title: TextView = itemView.findViewById(R.id.item_title)
        private val subtitle: TextView = itemView.findViewById(R.id.item_subtitle)

        fun bind(item: SearchableItem, onClick: (SearchableItem) -> Unit) {
            category.text = item.categoryLabel
            title.text = item.title
            if (item.subtitle != null) {
                subtitle.visibility = View.VISIBLE
                subtitle.text = item.subtitle
            } else {
                subtitle.visibility = View.GONE
            }
            itemView.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SearchableItem>() {
            override fun areItemsTheSame(a: SearchableItem, b: SearchableItem) = a.id == b.id
            override fun areContentsTheSame(a: SearchableItem, b: SearchableItem) = a == b
        }
    }
}
