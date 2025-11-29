package com.electricdreams.numo.feature.autowithdraw

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.electricdreams.numo.R
import com.electricdreams.numo.core.model.Amount
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity for configuring automatic withdrawals.
 * 
 * Allows setting:
 * - Global enable/disable
 * - Default Lightning address for withdrawals
 * - Balance threshold that triggers withdrawal
 * - Percentage of balance to withdraw (90-98%)
 * 
 * Also displays history of past auto-withdrawals.
 */
class AutoWithdrawSettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: AutoWithdrawSettingsManager
    private lateinit var autoWithdrawManager: AutoWithdrawManager

    // Views
    private lateinit var enableSwitch: SwitchCompat
    private lateinit var lightningAddressInput: TextInputEditText
    private lateinit var thresholdInput: TextInputEditText
    private lateinit var percentageSlider: Slider
    private lateinit var percentageValueText: TextView
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var historyEmptyText: TextView

    private var isUpdatingUI = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auto_withdraw_settings)

        settingsManager = AutoWithdrawSettingsManager.getInstance(this)
        autoWithdrawManager = AutoWithdrawManager.getInstance(this)

        initViews()
        setupListeners()
        loadSettings()
        loadHistory()
    }

    private fun initViews() {
        findViewById<View>(R.id.back_button).setOnClickListener { finish() }

        enableSwitch = findViewById(R.id.enable_switch)
        lightningAddressInput = findViewById(R.id.lightning_address_input)
        thresholdInput = findViewById(R.id.threshold_input)
        percentageSlider = findViewById(R.id.percentage_slider)
        percentageValueText = findViewById(R.id.percentage_value_text)
        historyRecyclerView = findViewById(R.id.history_recycler_view)
        historyEmptyText = findViewById(R.id.history_empty_text)

        historyRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupListeners() {
        // Enable switch
        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUI) {
                settingsManager.setGloballyEnabled(isChecked)
                updateConfigFieldsEnabled(isChecked)
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
                    val threshold = s?.toString()?.toLongOrNull() 
                        ?: AutoWithdrawSettingsManager.DEFAULT_THRESHOLD_SATS
                    settingsManager.setDefaultThreshold(threshold)
                }
            }
        })

        // Percentage slider
        percentageSlider.addOnChangeListener { _, value, fromUser ->
            val percentage = value.toInt()
            percentageValueText.text = "$percentage%"
            if (fromUser && !isUpdatingUI) {
                settingsManager.setDefaultPercentage(percentage)
            }
        }
    }

    private fun loadSettings() {
        isUpdatingUI = true

        val enabled = settingsManager.isGloballyEnabled()
        enableSwitch.isChecked = enabled
        updateConfigFieldsEnabled(enabled)

        lightningAddressInput.setText(settingsManager.getDefaultLightningAddress())
        thresholdInput.setText(settingsManager.getDefaultThreshold().toString())
        
        val percentage = settingsManager.getDefaultPercentage()
        percentageSlider.value = percentage.toFloat()
        percentageValueText.text = "$percentage%"

        isUpdatingUI = false
    }

    private fun updateConfigFieldsEnabled(enabled: Boolean) {
        lightningAddressInput.isEnabled = enabled
        thresholdInput.isEnabled = enabled
        percentageSlider.isEnabled = enabled
    }

    private fun loadHistory() {
        val history = autoWithdrawManager.getHistory()
        
        if (history.isEmpty()) {
            historyEmptyText.visibility = View.VISIBLE
            historyRecyclerView.visibility = View.GONE
        } else {
            historyEmptyText.visibility = View.GONE
            historyRecyclerView.visibility = View.VISIBLE
            historyRecyclerView.adapter = AutoWithdrawHistoryAdapter(history)
        }
    }

    /**
     * Adapter for displaying auto-withdraw history.
     */
    private inner class AutoWithdrawHistoryAdapter(
        private val entries: List<AutoWithdrawHistoryEntry>
    ) : RecyclerView.Adapter<AutoWithdrawHistoryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val statusIcon: ImageView = view.findViewById(R.id.status_icon)
            val amountText: TextView = view.findViewById(R.id.amount_text)
            val addressText: TextView = view.findViewById(R.id.address_text)
            val timestampText: TextView = view.findViewById(R.id.timestamp_text)
            val statusBadge: TextView = view.findViewById(R.id.status_badge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_auto_withdraw_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = entries[position]

            // Amount
            val amount = Amount(entry.amountSats, Amount.Currency.BTC)
            holder.amountText.text = amount.toString()

            // Address
            holder.addressText.text = entry.lightningAddress

            // Timestamp
            val dateFormat = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
            holder.timestampText.text = dateFormat.format(Date(entry.timestamp))

            // Status icon and badge
            when (entry.status) {
                AutoWithdrawHistoryEntry.STATUS_COMPLETED -> {
                    holder.statusIcon.setImageResource(R.drawable.ic_check)
                    holder.statusIcon.setColorFilter(getColor(R.color.color_primary_green))
                    holder.statusBadge.text = getString(R.string.auto_withdraw_status_completed)
                    holder.statusBadge.setBackgroundResource(R.drawable.bg_badge_success)
                }
                AutoWithdrawHistoryEntry.STATUS_PENDING -> {
                    holder.statusIcon.setImageResource(R.drawable.ic_pending)
                    holder.statusIcon.setColorFilter(getColor(R.color.color_warning))
                    holder.statusBadge.text = getString(R.string.auto_withdraw_status_pending)
                    holder.statusBadge.setBackgroundResource(R.drawable.bg_badge_warning)
                }
                AutoWithdrawHistoryEntry.STATUS_FAILED -> {
                    holder.statusIcon.setImageResource(R.drawable.ic_close)
                    holder.statusIcon.setColorFilter(getColor(R.color.color_error))
                    holder.statusBadge.text = getString(R.string.auto_withdraw_status_failed)
                    holder.statusBadge.setBackgroundResource(R.drawable.bg_badge_error)
                }
            }
        }

        override fun getItemCount() = entries.size
    }
}
