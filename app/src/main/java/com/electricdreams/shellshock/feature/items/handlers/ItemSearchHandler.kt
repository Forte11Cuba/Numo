package com.electricdreams.shellshock.feature.items.handlers

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.electricdreams.shellshock.core.model.Item
import com.electricdreams.shellshock.core.util.ItemManager

/**
 * Handles item search and filtering logic for ItemSelectionActivity.
 */
class ItemSearchHandler(
    private val itemManager: ItemManager,
    private val searchInput: EditText,
    private val itemsRecyclerView: RecyclerView,
    private val emptyView: LinearLayout,
    private val noResultsView: LinearLayout,
    private val onItemsFiltered: (List<Item>) -> Unit
) {

    private var allItems: List<Item> = emptyList()
    private var filteredItems: List<Item> = emptyList()

    init {
        setupSearchListener()
    }

    private fun setupSearchListener() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterItems(s?.toString() ?: "")
            }
        })
    }

    /**
     * Load all items from the item manager and update the UI.
     */
    fun loadItems() {
        allItems = itemManager.getAllItems()
        filteredItems = allItems
        onItemsFiltered(filteredItems)
        updateEmptyState()
    }

    /**
     * Filter items based on search query.
     */
    fun filterItems(query: String) {
        filteredItems = if (query.isBlank()) {
            allItems
        } else {
            itemManager.searchItems(query)
        }
        onItemsFiltered(filteredItems)
        updateEmptyState()
    }

    /**
     * Update the empty/no results state based on current items.
     */
    private fun updateEmptyState() {
        val hasItems = allItems.isNotEmpty()
        val hasResults = filteredItems.isNotEmpty()
        val isSearching = searchInput.text.isNotBlank()

        when {
            !hasItems -> {
                emptyView.visibility = View.VISIBLE
                noResultsView.visibility = View.GONE
                itemsRecyclerView.visibility = View.GONE
            }
            !hasResults && isSearching -> {
                emptyView.visibility = View.GONE
                noResultsView.visibility = View.VISIBLE
                itemsRecyclerView.visibility = View.GONE
            }
            else -> {
                emptyView.visibility = View.GONE
                noResultsView.visibility = View.GONE
                itemsRecyclerView.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Get current filtered items.
     */
    fun getFilteredItems(): List<Item> = filteredItems

    /**
     * Get all loaded items.
     */
    fun getAllItems(): List<Item> = allItems
}
