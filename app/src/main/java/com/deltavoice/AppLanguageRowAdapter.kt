package com.deltavoice

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppLanguageRowAdapter(
    private val options: List<AppLocaleOption>,
    private val selectedIndex: Int,
    private val onSelect: (Int) -> Unit
) : RecyclerView.Adapter<AppLanguageRowAdapter.VH>() {

    class VH(root: View) : RecyclerView.ViewHolder(root) {
        val radio: RadioButton = root.findViewById(R.id.radio)
        val textPrimary: TextView = root.findViewById(R.id.text_primary)
        val textSecondary: TextView = root.findViewById(R.id.text_secondary)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app_language, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val opt = options[position]
        holder.textPrimary.text = opt.nativeLabel
        holder.textSecondary.text = opt.englishLabel
        holder.radio.isChecked = position == selectedIndex
        holder.itemView.setOnClickListener { onSelect(position) }
    }

    override fun getItemCount(): Int = options.size
}
