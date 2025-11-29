package com.electricdreams.numo.feature.settings

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.electricdreams.numo.R
import com.electricdreams.numo.core.cashu.CashuWalletManager
import com.electricdreams.numo.core.model.Amount
import com.electricdreams.numo.core.util.MintIconCache
import com.electricdreams.numo.core.util.MintManager
import com.electricdreams.numo.feature.scanner.QRScannerActivity
import com.electricdreams.numo.ui.components.AddMintInputCard
import com.electricdreams.numo.ui.components.MintListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Premium Apple/Google-like mint management screen.
 * 
 * Features:
 * - Lightning Mint hero card for primary receive mint
 * - Clean list with total balance header
 * - Tap mints to view details
 * - Smooth micro-animations
 */
class MintsSettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MintsSettings"
        private const val PREF_LIGHTNING_MINT = "lightning_mint_url"
        const val REQUEST_MINT_DETAILS = 1001
    }

    // Views
    private lateinit var backButton: ImageButton
    private lateinit var resetButton: ImageButton
    private lateinit var lightningMintSection: View
    private lateinit var lightningMintCard: CardView
    private lateinit var lightningIconContainer: FrameLayout
    private lateinit var lightningMintIcon: ImageView
    private lateinit var lightningMintName: TextView
    private lateinit var lightningMintUrlText: TextView
    private lateinit var lightningMintBalance: TextView
    private lateinit var allMintsHeader: TextView
    private lateinit var mintsCard: CardView
    private lateinit var totalBalanceHeader: LinearLayout
    private lateinit var totalBalanceValue: TextView
    private lateinit var totalBalanceDivider: View
    private lateinit var mintsContainer: LinearLayout
    private lateinit var addMintHeader: TextView
    private lateinit var addMintCard: AddMintInputCard
    private lateinit var emptyState: View

    // State
    private lateinit var mintManager: MintManager
    private var mintBalances = mutableMapOf<String, Long>()
    private var selectedLightningMint: String? = null
    private val mintItems = mutableMapOf<String, MintListItem>()

    // Activity result launchers
    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val qrValue = result.data?.getStringExtra(QRScannerActivity.EXTRA_QR_VALUE)
            qrValue?.let { url ->
                addMintCard.setMintUrl(normalizeUrl(url))
            }
        }
    }

    private val mintDetailsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Refresh UI after mint details changed
            val changedMint = result.data?.getStringExtra(MintDetailsActivity.EXTRA_MINT_URL)
            val isDeleted = result.data?.getBooleanExtra(MintDetailsActivity.EXTRA_DELETED, false) ?: false
            val isLightningMint = result.data?.getBooleanExtra(MintDetailsActivity.EXTRA_SET_AS_LIGHTNING, false) ?: false
            
            if (isDeleted && changedMint != null) {
                handleMintDeleted(changedMint)
            } else if (isLightningMint && changedMint != null) {
                setLightningMint(changedMint, animate = true)
            } else {
                loadMintsAndBalances()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mints_settings)

        MintIconCache.initialize(this)
        mintManager = MintManager.getInstance(this)

        // Load saved lightning mint preference
        selectedLightningMint = getPreferences(MODE_PRIVATE).getString(PREF_LIGHTNING_MINT, null)

        initViews()
        setupListeners()
        loadMintsAndBalances()
        startEntranceAnimations()
    }

    override fun onResume() {
        super.onResume()
        refreshBalances()
    }

    private fun initViews() {
        backButton = findViewById(R.id.back_button)
        resetButton = findViewById(R.id.reset_button)
        lightningMintSection = findViewById(R.id.lightning_mint_section)
        lightningMintCard = findViewById(R.id.lightning_mint_card)
        lightningIconContainer = findViewById(R.id.lightning_icon_container)
        lightningMintIcon = findViewById(R.id.lightning_mint_icon)
        lightningMintName = findViewById(R.id.lightning_mint_name)
        lightningMintUrlText = findViewById(R.id.lightning_mint_url)
        lightningMintBalance = findViewById(R.id.lightning_mint_balance)
        allMintsHeader = findViewById(R.id.all_mints_header)
        mintsCard = findViewById(R.id.mints_card)
        totalBalanceHeader = findViewById(R.id.total_balance_header)
        totalBalanceValue = findViewById(R.id.total_balance_value)
        totalBalanceDivider = findViewById(R.id.total_balance_divider)
        mintsContainer = findViewById(R.id.mints_container)
        addMintHeader = findViewById(R.id.add_mint_header)
        addMintCard = findViewById(R.id.add_mint_card)
        emptyState = findViewById(R.id.empty_state)
    }

    private fun setupListeners() {
        backButton.setOnClickListener { 
            finish() 
        }

        resetButton.setOnClickListener {
            showResetConfirmation()
        }

        addMintCard.setOnAddMintListener(object : AddMintInputCard.OnAddMintListener {
            override fun onAddMint(mintUrl: String) {
                addNewMint(mintUrl)
            }

            override fun onScanQR() {
                openQRScanner()
            }
        })
    }

    private fun openQRScanner() {
        val intent = Intent(this, QRScannerActivity::class.java).apply {
            putExtra(QRScannerActivity.EXTRA_TITLE, getString(R.string.mints_scan_mint_qr))
            putExtra(QRScannerActivity.EXTRA_INSTRUCTION, getString(R.string.mints_scan_instruction))
        }
        qrScannerLauncher.launch(intent)
    }

    private fun loadMintsAndBalances() {
        val mints = mintManager.getAllowedMints()
        
        if (mints.isEmpty()) {
            showEmptyState()
            return
        }

        lifecycleScope.launch {
            // Load balances
            val balances = withContext(Dispatchers.IO) {
                CashuWalletManager.getAllMintBalances()
            }
            mintBalances.clear()
            mintBalances.putAll(balances)
            
            // Auto-select lightning mint if none selected
            if (selectedLightningMint == null || !mints.contains(selectedLightningMint)) {
                val highestBalanceMint = mints.maxByOrNull { mintBalances[it] ?: 0L }
                highestBalanceMint?.let { setLightningMint(it, animate = false) }
            }
            
            // Build UI
            buildMintList(mints)
            updateLightningMintCard()
            updateTotalBalance()
            
            // Refresh stale mint info
            refreshStaleMintInfo()
        }
    }

    private fun refreshBalances() {
        lifecycleScope.launch {
            val balances = withContext(Dispatchers.IO) {
                CashuWalletManager.getAllMintBalances()
            }
            mintBalances.clear()
            mintBalances.putAll(balances)
            
            // Update UI
            val mints = mintManager.getAllowedMints()
            buildMintList(mints)
            updateLightningMintCard()
            updateTotalBalance()
        }
    }

    private fun buildMintList(mints: List<String>) {
        mintsContainer.removeAllViews()
        mintItems.clear()

        if (mints.isEmpty()) {
            showEmptyState()
            return
        }

        hideEmptyState()
        
        // Sort by balance (highest first)
        val sortedMints = mints.sortedByDescending { mintBalances[it] ?: 0L }
        
        sortedMints.forEachIndexed { index, mintUrl ->
            val item = MintListItem(this)
            val balance = mintBalances[mintUrl] ?: 0L
            val isLast = index == sortedMints.lastIndex

            item.bind(mintUrl, balance, isLast)
            
            item.setOnMintItemListener(object : MintListItem.OnMintItemListener {
                override fun onMintTapped(url: String) {
                    openMintDetails(url)
                }
            })

            mintsContainer.addView(item)
            mintItems[mintUrl] = item

            // Staggered entrance animation
            item.animateEntrance(index * 50L)
        }
    }

    private fun updateTotalBalance() {
        val totalBalance = mintBalances.values.sum()
        
        if (totalBalance > 0 && mintBalances.size > 1) {
            totalBalanceHeader.visibility = View.VISIBLE
            totalBalanceDivider.visibility = View.VISIBLE
            totalBalanceValue.text = Amount(totalBalance, Amount.Currency.BTC).toString()
            
            // Animate in
            totalBalanceHeader.alpha = 0f
            totalBalanceHeader.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        } else {
            totalBalanceHeader.visibility = View.GONE
            totalBalanceDivider.visibility = View.GONE
        }
    }

    private fun setLightningMint(mintUrl: String, animate: Boolean) {
        selectedLightningMint = mintUrl
        
        // Save preference
        getPreferences(MODE_PRIVATE).edit()
            .putString(PREF_LIGHTNING_MINT, mintUrl)
            .apply()
        
        // Update hero card
        updateLightningMintCard()
        
        if (animate) {
            // Animate lightning card update
            lightningMintCard.animate()
                .scaleX(0.98f)
                .scaleY(0.98f)
                .setDuration(100)
                .withEndAction {
                    lightningMintCard.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                .start()
            
            Toast.makeText(this, R.string.mints_lightning_changed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateLightningMintCard() {
        val url = selectedLightningMint
        if (url == null) {
            lightningMintSection.visibility = View.GONE
            return
        }
        
        lightningMintSection.visibility = View.VISIBLE
        
        val displayName = mintManager.getMintDisplayName(url)
        val shortUrl = url.removePrefix("https://").removePrefix("http://")
        val balance = mintBalances[url] ?: 0L
        
        lightningMintName.text = displayName
        lightningMintUrlText.text = shortUrl
        lightningMintBalance.text = Amount(balance, Amount.Currency.BTC).toString()
        
        // Load icon
        loadLightningMintIcon(url)
    }

    private fun loadLightningMintIcon(url: String) {
        val cachedFile = MintIconCache.getCachedIconFile(url)
        if (cachedFile != null) {
            try {
                val bitmap = BitmapFactory.decodeFile(cachedFile.absolutePath)
                if (bitmap != null) {
                    lightningMintIcon.setImageBitmap(bitmap)
                    lightningMintIcon.clearColorFilter()
                    return
                }
            } catch (e: Exception) {
                // Fall through to default
            }
        }
        
        lightningMintIcon.setImageResource(R.drawable.ic_bitcoin)
        lightningMintIcon.setColorFilter(getColor(R.color.color_primary))
    }

    private fun openMintDetails(mintUrl: String) {
        val intent = Intent(this, MintDetailsActivity::class.java).apply {
            putExtra(MintDetailsActivity.EXTRA_MINT_URL, mintUrl)
            putExtra(MintDetailsActivity.EXTRA_IS_LIGHTNING_MINT, mintUrl == selectedLightningMint)
        }
        mintDetailsLauncher.launch(intent)
    }

    private fun handleMintDeleted(mintUrl: String) {
        mintBalances.remove(mintUrl)
        mintItems.remove(mintUrl)
        
        // If deleted mint was lightning mint, select new one
        if (selectedLightningMint == mintUrl) {
            val mints = mintManager.getAllowedMints()
            val newLightning = mints.maxByOrNull { mintBalances[it] ?: 0L }
            if (newLightning != null) {
                setLightningMint(newLightning, animate = true)
            } else {
                selectedLightningMint = null
                lightningMintSection.visibility = View.GONE
            }
        }
        
        if (mintManager.getAllowedMints().isEmpty()) {
            showEmptyState()
        } else {
            loadMintsAndBalances()
        }
    }

    private fun addNewMint(rawUrl: String) {
        val mintUrl = normalizeUrl(rawUrl)
        
        if (mintManager.getAllowedMints().contains(mintUrl)) {
            Toast.makeText(this, getString(R.string.mints_already_exists), Toast.LENGTH_SHORT).show()
            return
        }

        addMintCard.setLoading(true)

        lifecycleScope.launch {
            val isValid = validateMintUrl(mintUrl)
            
            if (!isValid) {
                addMintCard.setLoading(false)
                Toast.makeText(
                    this@MintsSettingsActivity,
                    getString(R.string.mints_invalid_url),
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val added = mintManager.addMint(mintUrl)
            if (added) {
                fetchAndStoreMintInfo(mintUrl)
                loadMintsAndBalances()
                addMintCard.clearInput()
                addMintCard.collapseIfExpanded()
                
                Toast.makeText(
                    this@MintsSettingsActivity,
                    getString(R.string.mints_added_toast),
                    Toast.LENGTH_SHORT
                ).show()
            }
            
            addMintCard.setLoading(false)
        }
    }

    private fun normalizeUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        if (url.endsWith("/")) {
            url = url.dropLast(1)
        }
        return url
    }

    private suspend fun validateMintUrl(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val infoUrl = "$url/v1/info"
            
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            
            val request = Request.Builder()
                .url(infoUrl)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            val isValid = response.isSuccessful && response.code == 200
            response.close()
            isValid
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun fetchAndStoreMintInfo(mintUrl: String) {
        withContext(Dispatchers.IO) {
            val info = CashuWalletManager.fetchMintInfo(mintUrl)
            if (info != null) {
                val json = CashuWalletManager.mintInfoToJson(info)
                mintManager.setMintInfo(mintUrl, json)
                mintManager.setMintRefreshTimestamp(mintUrl)
                
                info.iconUrl?.let { iconUrl ->
                    if (iconUrl.isNotEmpty()) {
                        MintIconCache.downloadAndCacheIcon(mintUrl, iconUrl)
                    }
                }
            }
        }
    }

    private fun refreshStaleMintInfo() {
        lifecycleScope.launch {
            val mintsToRefresh = mintManager.getMintsNeedingRefresh()
            for (mintUrl in mintsToRefresh) {
                fetchAndStoreMintInfo(mintUrl)
            }
            if (mintsToRefresh.isNotEmpty()) {
                updateLightningMintCard()
            }
        }
    }

    private fun showResetConfirmation() {
        AlertDialog.Builder(this, R.style.Theme_Numo_Dialog)
            .setTitle(getString(R.string.mints_reset_title))
            .setMessage(getString(R.string.mints_reset_message))
            .setPositiveButton(getString(R.string.mints_reset_confirm)) { _, _ ->
                resetToDefaults()
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun resetToDefaults() {
        mintManager.resetToDefaults()
        selectedLightningMint = null
        getPreferences(MODE_PRIVATE).edit().remove(PREF_LIGHTNING_MINT).apply()
        loadMintsAndBalances()
        Toast.makeText(this, getString(R.string.mints_reset_toast), Toast.LENGTH_SHORT).show()
    }

    private fun showEmptyState() {
        emptyState.visibility = View.VISIBLE
        mintsCard.visibility = View.GONE
        lightningMintSection.visibility = View.GONE
        allMintsHeader.visibility = View.GONE
    }

    private fun hideEmptyState() {
        emptyState.visibility = View.GONE
        mintsCard.visibility = View.VISIBLE
        allMintsHeader.visibility = View.VISIBLE
    }

    private fun startEntranceAnimations() {
        // Lightning mint section slide in
        lightningMintSection.alpha = 0f
        lightningMintSection.translationY = -30f
        lightningMintSection.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Add mint card entrance
        addMintCard.animateEntrance(400)
    }
}
