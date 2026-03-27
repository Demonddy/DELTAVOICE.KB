package com.deltavoice.search

import android.os.Bundle

/**
 * One row in the in-app search results list.
 */
data class SearchableItem(
    val id: String,
    val categoryLabel: String,
    val title: String,
    val subtitle: String?,
    private val matchBlob: String,
    val action: SearchAction
) {
    fun matches(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return false
        return matchBlob.contains(q)
    }
}

sealed class SearchAction {
    data class LaunchActivity(
        val clazz: Class<*>,
        val extras: Bundle? = null
    ) : SearchAction()

    object OpenKeyboardSettings : SearchAction()

    data class CopyToClipboard(val text: String) : SearchAction()
}
