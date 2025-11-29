package com.electricdreams.numo.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.electricdreams.numo.R
import com.electricdreams.numo.core.cashu.CashuWalletManager
import com.electricdreams.numo.core.model.Amount
import com.electricdreams.numo.core.util.MintIconCache
import com.electricdreams.numo.core.util.MintManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Premium mint details screen inspired by cashu-me MintDetailsPage.
 * 
 * Displays comprehensive mint information in a beautiful, organized layout:
 * - Header with icon, name, and balance
 * - Description and MOTD sections
 * - Contact information
 * - Technical details (URL, version, supported features)
 * - Action buttons (copy URL, delete)
 */
class MintDetailsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MINT_URL = "mint_url"
        private const val TAG = "MintDetails"
    }

    // Header views
    private lateinit var backButton: ImageButton
    private lateinit var iconContainer: FrameLayout
    private lateinit var mintIcon: ImageView
    private lateinit var mintName: TextView
    private lateinit var mintUrlText: TextView
    private lateinit var balanceText: TextView

    // Content sections
    private lateinit var descriptionSection: LinearLayout
    private lateinit var descriptionText: TextView
    private lateinit var motdSection: LinearLayout
    private lateinit var motdText: TextView
    
    // Contact section
    private lateinit var contactSection: LinearLayout
    private lateinit var contactContainer: LinearLayout
    
    // Details section
    private lateinit var detailsSection: LinearLayout
    private lateinit var urlRow: View
    private lateinit var urlValue: TextView
    private lateinit var versionRow: View
    private lateinit var versionValue: TextView
    private lateinit var nutsRow: View
    private lateinit var nutsValue: TextView
    
    // Actions
    private lateinit var copyUrlButton: LinearLayout
    private lateinit var deleteButton: LinearLayout

    // State
    private lateinit var mintManager: MintManager
    private var mintUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mint_details)

        mintUrl = intent.getStringExtra(EXTRA_MINT_URL) ?: run {
            finish()
            return
        }

        mintManager = MintManager.getInstance(this)
        MintIconCache.initialize(this)

        initViews()
        setupListeners()
        loadMintDetails()
        startEntranceAnimations()
    }

    private fun initViews() {
        backButton = findViewById(R.id.back_button)
        iconContainer = findViewById(R.id.icon_container)
        mintIcon = findViewById(R.id.mint_icon)
        mintName = findViewById(R.id.mint_name)
        mintUrlText = findViewById(R.id.mint_url)
        balanceText = findViewById(R.id.balance_text)
        
        descriptionSection = findViewById(R.id.description_section)
        descriptionText = findViewById(R.id.description_text)
        motdSection = findViewById(R.id.motd_section)
        motdText = findViewById(R.id.motd_text)
        
        contactSection = findViewById(R.id.contact_section)
        contactContainer = findViewById(R.id.contact_container)
        
        detailsSection = findViewById(R.id.details_section)
        urlRow = findViewById(R.id.url_row)
        urlValue = findViewById(R.id.url_value)
        versionRow = findViewById(R.id.version_row)
        versionValue = findViewById(R.id.version_value)
        nutsRow = findViewById(R.id.nuts_row)
        nutsValue = findViewById(R.id.nuts_value)
        
        copyUrlButton = findViewById(R.id.copy_url_button)
        deleteButton = findViewById(R.id.delete_button)
    }

    private fun setupListeners() {
        backButton.setOnClickListener {
            finish()
        }
        
        urlRow.setOnClickListener {
            copyToClipboard(mintUrl)
        }
        
        copyUrlButton.setOnClickListener {
            animateButtonTap(it) {
                copyToClipboard(mintUrl)
            }
        }
        
        deleteButton.setOnClickListener {
            animateButtonTap(it) {
                showDeleteConfirmation()
            }
        }
    }

    private fun loadMintDetails() {
        // Basic info
        val displayName = mintManager.getMintDisplayName(mintUrl)
        val shortUrl = mintUrl.removePrefix("https://").removePrefix("http://")
        
        mintName.text = displayName
        mintUrlText.text = shortUrl
        urlValue.text = shortUrl
        
        // Load icon
        loadMintIcon()
        
        // Load balance
        loadBalance()
        
        // Load mint info
        loadMintInfo()
    }

    private fun loadMintIcon() {
        val cachedFile = MintIconCache.getCachedIconFile(mintUrl)
        if (cachedFile != null) {
            try {
                val bitmap = BitmapFactory.decodeFile(cachedFile.absolutePath)
                if (bitmap != null) {
                    mintIcon.setImageBitmap(bitmap)
                    mintIcon.clearColorFilter()
                    return
                }
            } catch (e: Exception) {
                // Fall through to default
            }
        }
        
        mintIcon.setImageResource(R.drawable.ic_bitcoin)
        mintIcon.setColorFilter(getColor(R.color.color_primary))
    }

    private fun loadBalance() {
        lifecycleScope.launch {
            val balances = withContext(Dispatchers.IO) {
                CashuWalletManager.getAllMintBalances()
            }
            val balance = balances[mintUrl] ?: 0L
            balanceText.text = Amount(balance, Amount.Currency.BTC).toString()
        }
    }

    private fun loadMintInfo() {
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) {
                CashuWalletManager.fetchMintInfo(mintUrl)
            }
            
            if (info == null) {
                // Hide optional sections
                descriptionSection.visibility = View.GONE
                motdSection.visibility = View.GONE
                contactSection.visibility = View.GONE
                versionRow.visibility = View.GONE
                nutsRow.visibility = View.GONE
                return@launch
            }
            
            // Description (use descriptionLong if available, else description)
            val descriptionValue = info.descriptionLong ?: info.description
            if (!descriptionValue.isNullOrBlank()) {
                descriptionSection.visibility = View.VISIBLE
                descriptionText.text = descriptionValue
            } else {
                descriptionSection.visibility = View.GONE
            }
            
            // MOTD (Message of the Day)
            if (!info.motd.isNullOrBlank()) {
                motdSection.visibility = View.VISIBLE
                motdText.text = info.motd
            } else {
                motdSection.visibility = View.GONE
            }
            
            // Version
            val versionString = info.version?.toString()
            if (!versionString.isNullOrBlank()) {
                versionRow.visibility = View.VISIBLE
                versionValue.text = versionString
            } else {
                versionRow.visibility = View.GONE
            }
            
            // Hide NUTs and contact sections (not available in CDK MintInfo)
            nutsRow.visibility = View.GONE
            contactSection.visibility = View.GONE
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Mint URL", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.mint_details_copied, Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteConfirmation() {
        val displayName = mintManager.getMintDisplayName(mintUrl)
        
        AlertDialog.Builder(this, R.style.Theme_Numo_Dialog)
            .setTitle(getString(R.string.mints_remove_title))
            .setMessage(getString(R.string.mints_remove_message, displayName))
            .setPositiveButton(getString(R.string.mints_remove_confirm)) { _, _ ->
                deleteMint()
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun deleteMint() {
        mintManager.removeMint(mintUrl)
        Toast.makeText(this, getString(R.string.mints_removed_toast), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun animateButtonTap(view: View, onComplete: () -> Unit) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(80)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .withEndAction { onComplete() }
                    .start()
            }
            .start()
    }

    private fun startEntranceAnimations() {
        // Icon bounce
        iconContainer.scaleX = 0f
        iconContainer.scaleY = 0f
        iconContainer.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(2f))
            .start()
        
        // Name fade in
        mintName.alpha = 0f
        mintName.translationY = 20f
        mintName.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(100)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
        
        // Details card slide up
        detailsSection.alpha = 0f
        detailsSection.translationY = 30f
        detailsSection.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(200)
            .setDuration(350)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }
}
