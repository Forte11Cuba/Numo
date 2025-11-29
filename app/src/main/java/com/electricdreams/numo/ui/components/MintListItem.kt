package com.electricdreams.numo.ui.components

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.electricdreams.numo.R
import com.electricdreams.numo.core.model.Amount
import com.electricdreams.numo.core.util.MintIconCache
import com.electricdreams.numo.core.util.MintManager

/**
 * A premium Apple/Google-style mint list item matching MintSelectionBottomSheet design.
 * 
 * Features:
 * - Clean row-based layout without card elevation
 * - Subtle selection indicator with checkmark
 * - Expandable action buttons on long-press
 * - Smooth micro-animations for all interactions
 * - No green borders - uses muted, professional styling
 */
class MintListItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface OnMintItemListener {
        fun onMintTapped(mintUrl: String)
        fun onMintLongPressed(mintUrl: String): Boolean
        fun onDeleteClicked(mintUrl: String)
        fun onInfoClicked(mintUrl: String)
    }

    // Main row views
    private val container: View
    private val iconContainer: FrameLayout
    private val mintIcon: ImageView
    private val selectedBadge: View
    private val nameText: TextView
    private val urlText: TextView
    private val balanceText: TextView
    private val chevron: ImageView
    
    // Expanded action buttons
    private val actionsContainer: LinearLayout
    private val infoButton: View
    private val deleteButton: View
    
    private var mintUrl: String = ""
    private var listener: OnMintItemListener? = null
    private var isSelectedAsPrimary = false
    private var isExpanded = false

    init {
        LayoutInflater.from(context).inflate(R.layout.component_mint_list_item, this, true)
        
        container = findViewById(R.id.mint_item_container)
        iconContainer = findViewById(R.id.icon_container)
        mintIcon = findViewById(R.id.mint_icon)
        selectedBadge = findViewById(R.id.selected_badge)
        nameText = findViewById(R.id.mint_name)
        urlText = findViewById(R.id.mint_url)
        balanceText = findViewById(R.id.balance_text)
        chevron = findViewById(R.id.chevron)
        actionsContainer = findViewById(R.id.actions_container)
        infoButton = findViewById(R.id.info_button)
        deleteButton = findViewById(R.id.delete_button)
        
        setupClickListeners()
    }

    private fun setupClickListeners() {
        container.setOnClickListener {
            if (isExpanded) {
                collapseActions()
            } else {
                animateTap()
                listener?.onMintTapped(mintUrl)
            }
        }
        
        container.setOnLongClickListener {
            if (!isExpanded) {
                expandActions()
            }
            true
        }
        
        infoButton.setOnClickListener {
            animateButtonTap(it) {
                listener?.onInfoClicked(mintUrl)
            }
        }
        
        deleteButton.setOnClickListener {
            animateButtonTap(it) {
                listener?.onDeleteClicked(mintUrl)
            }
        }
    }

    fun bind(url: String, balance: Long, isPrimary: Boolean = false) {
        mintUrl = url
        isSelectedAsPrimary = isPrimary
        isExpanded = false
        actionsContainer.visibility = View.GONE
        actionsContainer.alpha = 0f
        
        // Get mint info
        val mintManager = MintManager.getInstance(context)
        val displayName = mintManager.getMintDisplayName(url)
        val shortUrl = url.removePrefix("https://").removePrefix("http://")
        
        nameText.text = displayName
        urlText.text = shortUrl
        balanceText.text = Amount(balance, Amount.Currency.BTC).toString()
        
        // Update selection state
        updatePrimaryState(isPrimary, animate = false)
        
        // Load icon
        loadIcon(url)
    }

    private fun loadIcon(url: String) {
        val cachedFile = MintIconCache.getCachedIconFile(url)
        if (cachedFile != null) {
            try {
                val bitmap = BitmapFactory.decodeFile(cachedFile.absolutePath)
                if (bitmap != null) {
                    mintIcon.setImageBitmap(bitmap)
                    mintIcon.clipToOutline = true
                    return
                }
            } catch (e: Exception) {
                // Fall through to default
            }
        }
        
        mintIcon.setImageResource(R.drawable.ic_bitcoin)
        mintIcon.setColorFilter(context.getColor(R.color.color_primary))
    }

    fun updatePrimaryState(isPrimary: Boolean, animate: Boolean = true) {
        isSelectedAsPrimary = isPrimary
        
        if (isPrimary) {
            // Show selection badge with animation
            if (animate) {
                selectedBadge.visibility = View.VISIBLE
                selectedBadge.alpha = 0f
                selectedBadge.scaleX = 0.3f
                selectedBadge.scaleY = 0.3f
                selectedBadge.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
                
                // Subtle highlight pulse on container
                container.animate()
                    .alpha(0.95f)
                    .setDuration(100)
                    .withEndAction {
                        container.animate()
                            .alpha(1f)
                            .setDuration(100)
                            .start()
                    }
                    .start()
            } else {
                selectedBadge.visibility = View.VISIBLE
                selectedBadge.alpha = 1f
                selectedBadge.scaleX = 1f
                selectedBadge.scaleY = 1f
            }
            
            // Change balance color to accent
            balanceText.setTextColor(context.getColor(R.color.color_primary))
            
            // Hide chevron when selected
            chevron.animate()
                .alpha(0f)
                .setDuration(150)
                .start()
        } else {
            // Hide selection badge
            if (animate && selectedBadge.visibility == View.VISIBLE) {
                selectedBadge.animate()
                    .alpha(0f)
                    .scaleX(0.3f)
                    .scaleY(0.3f)
                    .setDuration(200)
                    .withEndAction { selectedBadge.visibility = View.GONE }
                    .start()
            } else {
                selectedBadge.visibility = View.GONE
            }
            
            // Reset balance color
            balanceText.setTextColor(context.getColor(R.color.color_text_secondary))
            
            // Show chevron
            chevron.animate()
                .alpha(1f)
                .setDuration(150)
                .start()
        }
    }

    private fun expandActions() {
        isExpanded = true
        
        // Slide in actions container
        actionsContainer.visibility = View.VISIBLE
        actionsContainer.alpha = 0f
        actionsContainer.translationY = -20f
        
        actionsContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .start()
        
        // Animate buttons staggered
        infoButton.alpha = 0f
        infoButton.translationX = -30f
        infoButton.animate()
            .alpha(1f)
            .translationX(0f)
            .setStartDelay(50)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
        
        deleteButton.alpha = 0f
        deleteButton.translationX = -30f
        deleteButton.animate()
            .alpha(1f)
            .translationX(0f)
            .setStartDelay(100)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
        
        // Rotate chevron
        chevron.animate()
            .rotation(90f)
            .setDuration(200)
            .start()
        
        // Haptic feedback
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    }

    private fun collapseActions() {
        isExpanded = false
        
        actionsContainer.animate()
            .alpha(0f)
            .translationY(-10f)
            .setDuration(150)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                actionsContainer.visibility = View.GONE
            }
            .start()
        
        // Reset chevron
        chevron.animate()
            .rotation(0f)
            .setDuration(200)
            .start()
    }

    private fun animateTap() {
        container.animate()
            .scaleX(0.98f)
            .scaleY(0.98f)
            .setDuration(80)
            .withEndAction {
                container.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    private fun animateButtonTap(view: View, onComplete: () -> Unit) {
        view.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
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

    fun animateEntrance(delay: Long) {
        alpha = 0f
        translationY = 30f
        
        animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delay)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .start()
        
        // Icon bounce
        iconContainer.scaleX = 0f
        iconContainer.scaleY = 0f
        iconContainer.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(delay + 100)
            .setDuration(350)
            .setInterpolator(OvershootInterpolator(2f))
            .start()
    }

    fun animateRemoval(onComplete: () -> Unit) {
        animate()
            .alpha(0f)
            .translationX(width.toFloat())
            .setDuration(250)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction { onComplete() }
            .start()
    }

    fun collapseIfExpanded() {
        if (isExpanded) {
            collapseActions()
        }
    }

    fun setOnMintItemListener(listener: OnMintItemListener) {
        this.listener = listener
    }

    fun getMintUrl(): String = mintUrl
}
