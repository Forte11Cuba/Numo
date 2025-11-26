package com.electricdreams.shellshock.feature.items

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.electricdreams.shellshock.R
import com.electricdreams.shellshock.core.model.Item
import com.electricdreams.shellshock.core.util.ItemManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections

class ItemListActivity : AppCompatActivity() {

    private lateinit var itemManager: ItemManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var bottomActions: LinearLayout
    private lateinit var adapter: ItemAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    private val addItemLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                refreshItems()
                setResult(Activity.RESULT_OK) // Propagate result back
            }
        }

    private val csvPickerLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                importCsvFile(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_list)

        // Set up back button
        findViewById<View?>(R.id.back_button)?.setOnClickListener { finish() }

        recyclerView = findViewById(R.id.items_recycler_view)
        emptyView = findViewById(R.id.empty_view)
        bottomActions = findViewById(R.id.bottom_actions)
        val fabAddItem: ImageButton = findViewById(R.id.fab_add_item)
        val importCsvButton: Button = findViewById(R.id.import_csv_button)
        val clearItemsButton: TextView = findViewById(R.id.clear_items_button)

        itemManager = ItemManager.getInstance(this)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ItemAdapter(itemManager.getAllItems())
        recyclerView.adapter = adapter

        // Set up drag-and-drop reordering
        setupDragAndDrop()

        updateEmptyViewVisibility()

        fabAddItem.setOnClickListener {
            val intent = Intent(this, ItemEntryActivity::class.java)
            addItemLauncher.launch(intent)
        }

        importCsvButton.setOnClickListener {
            csvPickerLauncher.launch("text/csv")
        }

        clearItemsButton.setOnClickListener {
            showClearAllDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshItems()
    }

    private fun refreshItems() {
        adapter.updateItems(itemManager.getAllItems())
        updateEmptyViewVisibility()
    }

    private fun updateEmptyViewVisibility() {
        val hasItems = adapter.itemCount > 0
        if (hasItems) {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        } else {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }
    }

    private fun showClearAllDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Items")
            .setMessage("Are you sure you want to delete ALL items from your catalog? This cannot be undone.")
            .setPositiveButton("Delete All") { _, _ ->
                itemManager.clearItems()
                refreshItems()
                setResult(Activity.RESULT_OK)
                Toast.makeText(this, "All items cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importCsvFile(uri: Uri) {
        try {
            val tempFile = File(cacheDir, "import_catalog.csv")

            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    copyStream(inputStream, outputStream)
                }
            } ?: run {
                Toast.makeText(this, "Failed to open CSV file", Toast.LENGTH_SHORT).show()
                return
            }

            val importedCount = itemManager.importItemsFromCsv(tempFile.absolutePath, true)

            if (importedCount > 0) {
                Toast.makeText(this, "Imported $importedCount items", Toast.LENGTH_SHORT).show()
                refreshItems()
                setResult(Activity.RESULT_OK)
            } else {
                Toast.makeText(this, "No items imported from CSV", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error importing CSV file: ${e.message}", e)
            Toast.makeText(this, "Error importing CSV file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyStream(inputStream: InputStream, outputStream: OutputStream) {
        val buffer = ByteArray(4096)
        var bytesRead: Int
        while (true) {
            bytesRead = inputStream.read(buffer)
            if (bytesRead == -1) break
            outputStream.write(buffer, 0, bytesRead)
        }
    }

    private fun setupDragAndDrop() {
        val callback = object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                return makeMovementFlags(dragFlags, 0)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                adapter.moveItem(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not used - swipe disabled
            }

            override fun isLongPressDragEnabled(): Boolean = false

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.9f
                    viewHolder?.itemView?.elevation = 8f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
                viewHolder.itemView.elevation = 0f
                // Persist the new order when drag ends
                adapter.commitReorder()
            }
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    fun startDragging(viewHolder: RecyclerView.ViewHolder) {
        itemTouchHelper.startDrag(viewHolder)
    }

    private inner class ItemAdapter(items: List<Item>) :
        RecyclerView.Adapter<ItemAdapter.ItemViewHolder>() {

        private val itemsList: MutableList<Item> = items.toMutableList()
        private var pendingFromPosition: Int = -1
        private var pendingToPosition: Int = -1

        fun updateItems(newItems: List<Item>) {
            itemsList.clear()
            itemsList.addAll(newItems)
            pendingFromPosition = -1
            pendingToPosition = -1
            notifyDataSetChanged()
        }

        fun moveItem(fromPosition: Int, toPosition: Int) {
            if (fromPosition < 0 || fromPosition >= itemsList.size ||
                toPosition < 0 || toPosition >= itemsList.size) {
                return
            }
            // Track the overall drag operation (from start to end)
            if (pendingFromPosition == -1) {
                pendingFromPosition = fromPosition
            }
            pendingToPosition = toPosition

            // Move item in local list for visual feedback
            Collections.swap(itemsList, fromPosition, toPosition)
            notifyItemMoved(fromPosition, toPosition)
        }

        fun commitReorder() {
            if (pendingFromPosition != -1 && pendingToPosition != -1 &&
                pendingFromPosition != pendingToPosition) {
                // Persist the reorder to storage
                itemManager.reorderItems(pendingFromPosition, pendingToPosition)
                setResult(Activity.RESULT_OK)
            }
            pendingFromPosition = -1
            pendingToPosition = -1
            // Refresh dividers after reorder
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_product, parent, false)
            return ItemViewHolder(view)
        }

        override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
            val item = itemsList[position]
            holder.bind(item, position == itemsList.size - 1)
        }

        override fun getItemCount(): Int = itemsList.size

        inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val nameView: TextView = itemView.findViewById(R.id.item_name)
            private val variationView: TextView = itemView.findViewById(R.id.item_variation)
            private val priceView: TextView = itemView.findViewById(R.id.item_price)
            private val quantityView: TextView = itemView.findViewById(R.id.item_quantity)
            private val itemImageView: ImageView = itemView.findViewById(R.id.item_image)
            private val imagePlaceholder: ImageView = itemView.findViewById(R.id.item_image_placeholder)
            private val divider: View = itemView.findViewById(R.id.divider)
            private val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle)

            @SuppressLint("ClickableViewAccessibility")
            fun bind(item: Item, isLast: Boolean) {
                // Item name
                nameView.text = item.name ?: ""

                // Variation (grey text)
                if (!item.variationName.isNullOrEmpty()) {
                    variationView.text = item.variationName
                    variationView.visibility = View.VISIBLE
                } else {
                    variationView.visibility = View.GONE
                }

                // Price
                priceView.text = item.getFormattedPrice()

                // Stock quantity (only if tracking inventory)
                if (item.trackInventory) {
                    quantityView.text = "${item.quantity} in stock"
                    quantityView.visibility = View.VISIBLE
                } else {
                    quantityView.visibility = View.GONE
                }

                // Image
                if (!item.imagePath.isNullOrEmpty()) {
                    val bitmap = itemManager.loadItemImage(item)
                    if (bitmap != null) {
                        itemImageView.setImageBitmap(bitmap)
                        imagePlaceholder.visibility = View.GONE
                    } else {
                        itemImageView.setImageBitmap(null)
                        imagePlaceholder.visibility = View.VISIBLE
                    }
                } else {
                    itemImageView.setImageBitmap(null)
                    imagePlaceholder.visibility = View.VISIBLE
                }

                // Hide divider on last item
                divider.visibility = if (isLast) View.GONE else View.VISIBLE

                // Drag handle - start dragging on touch
                dragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        startDragging(this)
                    }
                    false
                }

                // Click to edit
                itemView.setOnClickListener {
                    val intent = Intent(this@ItemListActivity, ItemEntryActivity::class.java)
                    intent.putExtra(ItemEntryActivity.EXTRA_ITEM_ID, item.id)
                    addItemLauncher.launch(intent)
                }
            }
        }
    }

    companion object {
        private const val TAG = "ItemListActivity"
    }
}
