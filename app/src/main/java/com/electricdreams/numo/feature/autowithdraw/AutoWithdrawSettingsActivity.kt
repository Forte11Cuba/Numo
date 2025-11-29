package com.electricdreams.numo.feature.autowithdraw

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.electricdreams.numo.R
import com.electricdreams.numo.core.model.Amount
import com.google.android.material.slider.Slider
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Premium Apple-like settings screen for automatic withdrawals.
 * 
 * Features a beautiful hero section, card-based settings groups,
 * smooth animations, and a clean transaction history.
 */
class AutoWithdrawSettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: AutoWithdrawSettingsManager
    private lateinit var autoWithdrawManager: AutoWithdrawManager

    // Hero section
    private lateinit var heroIcon: ImageView
    private lateinit var heroIconContainer: FrameLayout
    private lateinit var statusContainer: LinearLayout
    private lateinit var statusDot: View
    private lateinit var statusText: TextView

    // Settings controls
    private lateinit var enableSwitch: SwitchCompat
    private lateinit var enableToggleRow: LinearLayout
    private lateinit var lightningAddressInput: EditText
    private lateinit var thresholdInput: EditText
    private lateinit var percentageSlider: Slider
    private lateinit var percentageBadge: TextView

    // History section
    private lateinit var historyCard: CardView
    private lateinit var historyEmptyContainer: LinearLayout
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var seeAllButton: TextView

    private var isUpdatingUI = false
    private var iconAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auto_withdraw_settings)

        settingsManager = AutoWithdrawSettingsManager.getInstance(this)
        autoWithdrawManager = AutoWithdrawManager.getInstance(this)

        setupToolbar()
        initViews()
        setupListeners()
        loadSettings()
        loadHistory()
        
        // Start entrance animations
        startEntranceAnimations()
    }

    private fun setupToolbar() {
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun initViews() {
        // Hero section
        heroIcon = findViewById(R.id.hero_icon)
        heroIconContainer = findViewById(R.id.icon_container)
        statusContainer = findViewById(R.id.status_container)
        statusDot = findViewById(R.id.status_dot)
        statusText = findViewById(R.id.status_text)

        // Main toggle
        enableSwitch = findViewById(R.id.enable_switch)
        enableToggleRow = findViewById(R.id.enable_toggle_row)

        // Config inputs
        lightningAddressInput = findViewById(R.id.lightning_address_input)
        thresholdInput = findViewById(R.id.threshold_input)
        percentageSlider = findViewById(R.id.percentage_slider)
        percentageBadge = findViewById(R.id.percentage_badge)

        // History
        historyCard = findViewById(R.id.history_card)
        historyEmptyContainer = findViewById(R.id.history_empty_container)
        historyRecyclerView = findViewById(R.id.history_recycler_view)
        seeAllButton = findViewById(R.id.see_all_button)

        historyRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupListeners() {
        // Toggle row click (toggles switch)
        enableToggleRow.setOnClickListener {
            enableSwitch.toggle()
        }

        // Enable switch
        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUI) {
                settingsManager.setGloballyEnabled(isChecked)
                updateStatusIndicator(isChecked)
                updateConfigFieldsEnabled(isChecked)
                animateStatusChange(isChecked)
            }
        }

        // Lightning address
        lightningAddressInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isUpdatingUI) {
                    settingsManager.setDefaultLightningAddress(s?.toString()?.trim() ?: "")
                }
            }
        })

        // Threshold input
        thresholdInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isUpdatingUI) {
                    val threshold = s?.toString()?.replace(",", "")?.toLongOrNull()
                        ?: AutoWithdrawSettingsManager.DEFAULT_THRESHOLD_SATS
                    settingsManager.setDefaultThreshold(threshold)
                }
            }
        })

        // Percentage slider with haptic feedback
        percentageSlider.addOnChangeListener { slider, value, fromUser ->
            val percentage = value.toInt()
            percentageBadge.text = "$percentage%"
            
            if (fromUser && !isUpdatingUI) {
                settingsManager.setDefaultPercentage(percentage)
                // Subtle haptic on step changes
                slider.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    private fun loadSettings() {
        isUpdatingUI = true

        val enabled = settingsManager.isGloballyEnabled()
        enableSwitch.isChecked = enabled
        updateStatusIndicator(enabled)
        updateConfigFieldsEnabled(enabled)

        lightningAddressInput.setText(settingsManager.getDefaultLightningAddress())
        
        val threshold = settingsManager.getDefaultThreshold()
        thresholdInput.setText(NumberFormat.getNumberInstance().format(threshold))

        val percentage = settingsManager.getDefaultPercentage()
        percentageSlider.value = percentage.toFloat()
        percentageBadge.text = "$percentage%"

        isUpdatingUI = false
    }

    private fun updateStatusIndicator(enabled: Boolean) {
        if (enabled) {
            statusDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.color_success_green)
            statusText.text = getString(R.string.auto_withdraw_status_active)
            statusText.setTextColor(ContextCompat.getColor(this, R.color.color_success_green))
            statusContainer.background = ContextCompat.getDrawable(this, R.drawable.bg_status_pill_success)
        } else {
            statusDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.color_text_tertiary)
            statusText.text = getString(R.string.auto_withdraw_status_inactive)
            statusText.setTextColor(ContextCompat.getColor(this, R.color.color_text_tertiary))
            statusContainer.background = ContextCompat.getDrawable(this, R.drawable.bg_input_pill)
        }
    }

    private fun animateStatusChange(enabled: Boolean) {
        // Pulse animation on status container
        val scaleX = ObjectAnimator.ofFloat(statusContainer, "scaleX", 1f, 1.1f, 1f)
        val scaleY = ObjectAnimator.ofFloat(statusContainer, "scaleY", 1f, 1.1f, 1f)
        
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 300
            interpolator = OvershootInterpolator()
            start()
        }

        // Icon pulse
        if (enabled) {
            startIconPulseAnimation()
        } else {
            stopIconPulseAnimation()
        }
    }

    private fun startIconPulseAnimation() {
        iconAnimator?.cancel()
        
        iconAnimator = ObjectAnimator.ofFloat(heroIconContainer, "alpha", 1f, 0.6f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopIconPulseAnimation() {
        iconAnimator?.cancel()
        heroIconContainer.alpha = 1f
    }

    private fun updateConfigFieldsEnabled(enabled: Boolean) {
        val alpha = if (enabled) 1f else 0.5f
        
        // Animate alpha change
        lightningAddressInput.animate().alpha(alpha).setDuration(200).start()
        thresholdInput.animate().alpha(alpha).setDuration(200).start()
        percentageSlider.animate().alpha(alpha).setDuration(200).start()
        percentageBadge.animate().alpha(alpha).setDuration(200).start()
        
        lightningAddressInput.isEnabled = enabled
        thresholdInput.isEnabled = enabled
        percentageSlider.isEnabled = enabled
    }

    private fun loadHistory() {
        val history = autoWithdrawManager.getHistory()

        if (history.isEmpty()) {
            historyEmptyContainer.visibility = View.VISIBLE
            historyRecyclerView.visibility = View.GONE
            seeAllButton.visibility = View.GONE
        } else {
            historyEmptyContainer.visibility = View.GONE
            historyRecyclerView.visibility = View.VISIBLE
            seeAllButton.visibility = if (history.size > 5) View.VISIBLE else View.GONE
            
            // Show only latest 5 entries
            val displayHistory = history.take(5)
            historyRecyclerView.adapter = AutoWithdrawHistoryAdapter(displayHistory)
        }
    }

    private fun startEntranceAnimations() {
        // Hero card slide in
        val heroCard: CardView = findViewById(R.id.hero_card)
        heroCard.alpha = 0f
        heroCard.translationY = -50f
        heroCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Icon bounce
        heroIconContainer.scaleX = 0f
        heroIconContainer.scaleY = 0f
        heroIconContainer.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(200)
            .setDuration(500)
            .setInterpolator(OvershootInterpolator(2f))
            .start()

        // Status pill fade
        statusContainer.alpha = 0f
        statusContainer.animate()
            .alpha(1f)
            .setStartDelay(400)
            .setDuration(300)
            .start()

        // Cards stagger in
        val toggleCard: CardView = findViewById(R.id.toggle_card)
        animateCardEntrance(toggleCard, 100)
        
        // If auto-withdraw is enabled, start icon animation
        if (settingsManager.isGloballyEnabled()) {
            heroIconContainer.postDelayed({ startIconPulseAnimation() }, 800)
        }
    }

    private fun animateCardEntrance(card: View, delay: Long) {
        card.alpha = 0f
        card.translationY = 30f
        card.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delay)
            .setDuration(350)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    override fun onDestroy() {
        super.onDestroy()
        iconAnimator?.cancel()
    }

    /**
     * Premium adapter for displaying auto-withdraw history.
     */
    private inner class AutoWithdrawHistoryAdapter(
        private val entries: List<AutoWithdrawHistoryEntry>
    ) : RecyclerView.Adapter<AutoWithdrawHistoryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val iconContainer: FrameLayout = view.findViewById(R.id.icon_container)
            val statusIcon: ImageView = view.findViewById(R.id.status_icon)
            val amountText: TextView = view.findViewById(R.id.amount_text)
            val addressText: TextView = view.findViewById(R.id.address_text)
            val timestampText: TextView = view.findViewById(R.id.timestamp_text)
            val statusBadge: TextView = view.findViewById(R.id.status_badge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_auto_withdraw_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = entries[position]

            // Format amount with locale-aware number formatting
            val formattedAmount = NumberFormat.getNumberInstance().format(entry.amountSats) + " sats"
            holder.amountText.text = formattedAmount

            // Truncate long addresses
            holder.addressText.text = entry.lightningAddress

            // Relative timestamp
            val dateFormat = SimpleDateFormat("MMM d • HH:mm", Locale.getDefault())
            holder.timestampText.text = dateFormat.format(Date(entry.timestamp))

            // Status styling
            when (entry.status) {
                AutoWithdrawHistoryEntry.STATUS_COMPLETED -> {
                    holder.statusIcon.setImageResource(R.drawable.ic_check)
                    holder.statusIcon.setColorFilter(ContextCompat.getColor(this@AutoWithdrawSettingsActivity, R.color.color_success_green))
                    holder.iconContainer.backgroundTintList = ContextCompat.getColorStateList(this@AutoWithdrawSettingsActivity, R.color.color_bg_secondary)
                    holder.statusBadge.text = getString(R.string.auto_withdraw_status_completed)
                    holder.statusBadge.setTextColor(ContextCompat.getColor(this@AutoWithdrawSettingsActivity, R.color.color_success_green))
                    holder.statusBadge.background = ContextCompat.getDrawable(this@AutoWithdrawSettingsActivity, R.drawable.bg_status_pill_success)
                }
                AutoWithdrawHistoryEntry.STATUS_PENDING -> {
                    holder.statusIcon.setImageResource(R.drawable.ic_pending)
                    holder.statusIcon.setColorFilter(ContextCompat.getColor(this@AutoWithdrawSettingsActivity, R.color.color_warning))
                    holder.iconContainer.backgroundTintList = ContextCompat.getColorStateList(this@AutoWithdrawSettingsActivity, R.color.color_bg_secondary)
                    holder.statusBadge.text = getString(R.string.auto_withdraw_status_pending)
                    holder.statusBadge.setTextColor(ContextCompat.getColor(this@AutoWithdrawSettingsActivity, R.color.color_warning))
                    holder.statusBadge.background = ContextCompat.getDrawable(this@AutoWithdrawSettingsActivity, R.drawable.bg_status_pill_pending)
                }
                AutoWithdrawHistoryEntry.STATUS_FAILED -> {
                    holder.statusIcon.setImageResource(R.drawable.ic_close)
                    holder.statusIcon.setColorFilter(ContextCompat.getColor(this@AutoWithdrawSettingsActivity, R.color.color_error))
                    holder.iconContainer.backgroundTintList = ContextCompat.getColorStateList(this@AutoWithdrawSettingsActivity, R.color.color_bg_secondary)
                    holder.statusBadge.text = getString(R.string.auto_withdraw_status_failed)
                    holder.statusBadge.setTextColor(ContextCompat.getColor(this@AutoWithdrawSettingsActivity, R.color.color_error))
                    holder.statusBadge.background = ContextCompat.getDrawable(this@AutoWithdrawSettingsActivity, R.drawable.bg_status_pill_error)
                }
            }

            // Animate item appearance
            holder.itemView.alpha = 0f
            holder.itemView.animate()
                .alpha(1f)
                .setStartDelay((position * 50).toLong())
                .setDuration(200)
                .start()
        }

        override fun getItemCount() = entries.size
    }
}
