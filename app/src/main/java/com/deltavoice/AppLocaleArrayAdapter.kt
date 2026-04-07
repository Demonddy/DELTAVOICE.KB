package com.deltavoice

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class AppLocaleArrayAdapter(
    context: Context,
    private val options: List<AppLocaleOption>
) : ArrayAdapter<AppLocaleOption>(
    context,
    android.R.layout.simple_list_item_2,
    android.R.id.text1,
    options
) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = super.getView(position, convertView, parent)
        val text1 = v.findViewById<TextView>(android.R.id.text1)
        val text2 = v.findViewById<TextView>(android.R.id.text2)
        val opt = options[position]
        text1.text = opt.nativeLabel
        text2.text = opt.englishLabel
        return v
    }
}
