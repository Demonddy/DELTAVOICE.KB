package com.deltavoice

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deltavoice.search.AppSearchIndex
import com.deltavoice.search.SearchAction
import com.deltavoice.search.SearchResultsAdapter
import com.deltavoice.search.SearchableItem

/**
 * In-app search across features, activity stats, policies, services, subscription, and clipboard.
 */
class SearchActivity : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: SearchResultsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        findViewById<ImageButton>(R.id.btn_exit_home).setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
            finish()
        }

        searchInput = findViewById(R.id.search_input)
        recyclerView = findViewById(R.id.search_results)
        emptyView = findViewById(R.id.search_empty)

        adapter = SearchResultsAdapter { item -> onResultClick(item) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                refreshResults()
                true
            } else false
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                refreshResults()
            }
        })

        showEmptyPrompt()
    }

    private fun showEmptyPrompt() {
        emptyView.visibility = View.VISIBLE
        emptyView.setText(R.string.search_empty_state)
        recyclerView.visibility = View.GONE
        adapter.submitList(emptyList())
    }

    private fun refreshResults() {
        val q = searchInput.text?.toString().orEmpty().trim()
        if (q.isEmpty()) {
            showEmptyPrompt()
            return
        }
        val results = AppSearchIndex.search(this, q)
        if (results.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            emptyView.setText(R.string.search_no_results)
            recyclerView.visibility = View.GONE
            adapter.submitList(emptyList())
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            adapter.submitList(results)
        }
    }

    private fun onResultClick(item: SearchableItem) {
        when (val a = item.action) {
            is SearchAction.LaunchActivity -> {
                startActivity(Intent(this, a.clazz).apply { a.extras?.let { putExtras(it) } })
            }
            SearchAction.OpenKeyboardSettings -> {
                try {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                } catch (_: Exception) {
                    Toast.makeText(this, getString(R.string.could_not_open_search), Toast.LENGTH_SHORT).show()
                }
            }
            is SearchAction.CopyToClipboard -> {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("deltavoice", a.text))
                Toast.makeText(this, getString(R.string.search_copied), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
