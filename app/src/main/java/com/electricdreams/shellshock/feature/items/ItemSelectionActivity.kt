package com.electricdreams.shellshock.feature.items

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.electricdreams.shellshock.R
import com.electricdreams.shellshock.core.util.BasketManager
import com.electricdreams.shellshock.core.util.CurrencyManager
import com.electricdreams.shellshock.core.util.ItemManager
import com.electricdreams.shellshock.core.worker.BitcoinPriceWorker
import com.electricdreams.shellshock.feature.items.adapters.SelectionBasketAdapter
import com.electricdreams.shellshock.feature.items.adapters.SelectionItemsAdapter
import com.electricdreams.shellshock.feature.items.handlers.BasketUIHandler
import com.electricdreams.shellshock.feature.items.handlers.CheckoutHandler
import com.electricdreams.shellshock.feature.items.handlers.ItemSearchHandler
import com.electricdreams.shellshock.feature.items.handlers.SelectionAnimationHandler

/**
 * Activity for selecting items and adding them to a basket for checkout.
 * Supports search, quantity adjustments, custom variations, and checkout flow.
 */
class ItemSelectionActivity : AppCompatActivity() {

    // ----- Managers -----
    private lateinit var itemManager: ItemManager
    private lateinit var basketManager: BasketManager
    private lateinit var bitcoinPriceWorker: BitcoinPriceWorker
    private lateinit var currencyManager: CurrencyManager

    // ----- Views -----
    private lateinit var mainScrollView: NestedScrollView
    private lateinit var searchInput: EditText
    private lateinit var scanButton: ImageButton
    private lateinit var basketSection: LinearLayout
    private lateinit var basketRecyclerView: RecyclerView
    private lateinit var basketTotalView: TextView
    private lateinit var clearBasketButton: TextView
    private lateinit var itemsRecyclerView: RecyclerView
    private lateinit var emptyView: LinearLayout
    private lateinit var noResultsView: LinearLayout
    private lateinit var checkoutContainer: CardView
    private lateinit var checkoutButton: Button

    // ----- Adapters -----
    private lateinit var itemsAdapter: SelectionItemsAdapter
    private lateinit var basketAdapter: SelectionBasketAdapter

    // ----- Handlers -----
    private lateinit var animationHandler: SelectionAnimationHandler
    private lateinit var basketUIHandler: BasketUIHandler
    private lateinit var searchHandler: ItemSearchHandler
    private lateinit var checkoutHandler: CheckoutHandler

    // ----- Activity Result Launchers -----
    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == CheckoutScannerActivity.RESULT_BASKET_UPDATED) {
            refreshBasket()
            itemsAdapter.notifyDataSetChanged()
        }
    }

    // ----- Lifecycle -----

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_selection)

        initializeManagers()
        initializeViews()
        initializeHandlers()
        initializeAdapters()
        setupRecyclerViews()
        setupClickListeners()

        // Load initial data
        searchHandler.loadItems()
        refreshBasket()

        bitcoinPriceWorker.start()
    }

    override fun onResume() {
        super.onResume()
        // Refresh in case items were modified
        searchHandler.loadItems()
        refreshBasket()
    }

    // ----- Initialization -----

    private fun initializeManagers() {
        itemManager = ItemManager.getInstance(this)
        basketManager = BasketManager.getInstance()
        bitcoinPriceWorker = BitcoinPriceWorker.getInstance(this)
        currencyManager = CurrencyManager.getInstance(this)
    }

    private fun initializeViews() {
        findViewById<View>(R.id.back_button).setOnClickListener { finish() }

        mainScrollView = findViewById(R.id.main_scroll_view)
        searchInput = findViewById(R.id.search_input)
        scanButton = findViewById(R.id.scan_button)
        basketSection = findViewById(R.id.basket_section)
        basketRecyclerView = findViewById(R.id.basket_recycler_view)
        basketTotalView = findViewById(R.id.basket_total)
        clearBasketButton = findViewById(R.id.clear_basket_button)
        itemsRecyclerView = findViewById(R.id.items_recycler_view)
        emptyView = findViewById(R.id.empty_view)
        noResultsView = findViewById(R.id.no_results_view)
        checkoutContainer = findViewById(R.id.checkout_container)
        checkoutButton = findViewById(R.id.checkout_button)
    }

    private fun initializeHandlers() {
        animationHandler = SelectionAnimationHandler(
            basketSection = basketSection,
            checkoutContainer = checkoutContainer
        )

        basketUIHandler = BasketUIHandler(
            basketManager = basketManager,
            currencyManager = currencyManager,
            basketTotalView = basketTotalView,
            checkoutButton = checkoutButton,
            animationHandler = animationHandler,
            onBasketUpdated = { basketAdapter.updateItems(basketManager.getBasketItems()) }
        )

        searchHandler = ItemSearchHandler(
            itemManager = itemManager,
            searchInput = searchInput,
            itemsRecyclerView = itemsRecyclerView,
            emptyView = emptyView,
            noResultsView = noResultsView,
            onItemsFiltered = { items -> itemsAdapter.updateItems(items) }
        )

        checkoutHandler = CheckoutHandler(
            activity = this,
            basketManager = basketManager,
            currencyManager = currencyManager,
            bitcoinPriceWorker = bitcoinPriceWorker
        )
    }

    private fun initializeAdapters() {
        itemsAdapter = SelectionItemsAdapter(
            context = this,
            basketManager = basketManager,
            mainScrollView = mainScrollView,
            onQuantityChanged = { refreshBasket() },
            onQuantityAnimation = { quantityView -> animationHandler.animateQuantityChange(quantityView) }
        )

        basketAdapter = SelectionBasketAdapter(
            currencyManager = currencyManager,
            onItemRemoved = { itemId -> handleItemRemoved(itemId) }
        )
    }

    private fun setupRecyclerViews() {
        itemsRecyclerView.layoutManager = LinearLayoutManager(this)
        itemsRecyclerView.adapter = itemsAdapter

        basketRecyclerView.layoutManager = LinearLayoutManager(this)
        basketRecyclerView.adapter = basketAdapter
    }

    private fun setupClickListeners() {
        scanButton.setOnClickListener {
            val intent = Intent(this, CheckoutScannerActivity::class.java)
            scannerLauncher.launch(intent)
        }

        clearBasketButton.setOnClickListener {
            showClearBasketDialog()
        }

        checkoutButton.setOnClickListener {
            checkoutHandler.proceedToCheckout()
        }
    }

    // ----- Actions -----

    private fun refreshBasket() {
        basketUIHandler.refreshBasket()
    }

    private fun handleItemRemoved(itemId: String) {
        basketManager.removeItem(itemId)
        itemsAdapter.resetItemQuantity(itemId)
        refreshBasket()
    }

    private fun showClearBasketDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear Basket")
            .setMessage("Remove all items from basket?")
            .setPositiveButton("Clear") { _, _ ->
                basketManager.clearBasket()
                itemsAdapter.clearAllQuantities()
                refreshBasket()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
