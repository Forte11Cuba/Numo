package com.electricdreams.numo.feature.autowithdraw

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Settings for automatic withdrawals for a specific mint.
 */
data class MintWithdrawSettings(
    val mintUrl: String,
    val enabled: Boolean = false,
    val thresholdSats: Long = 10000, // Default 10k sats threshold
    val withdrawPercentage: Int = 95, // Default 95% withdrawal (5% buffer for fees)
    val lightningAddress: String = ""
)

/**
 * Manages auto-withdrawal settings persistence.
 */
class AutoWithdrawSettingsManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "AutoWithdrawPreferences"
        private const val KEY_GLOBAL_ENABLED = "globalEnabled"
        private const val KEY_MINT_SETTINGS = "mintSettings"
        private const val KEY_DEFAULT_THRESHOLD = "defaultThreshold"
        private const val KEY_DEFAULT_PERCENTAGE = "defaultPercentage"
        private const val KEY_DEFAULT_LIGHTNING_ADDRESS = "defaultLightningAddress"
        
        const val MIN_THRESHOLD_SATS = 1000L // Minimum 1k sats
        const val MAX_THRESHOLD_SATS = 1000000L // Maximum 1M sats
        const val MIN_WITHDRAW_PERCENTAGE = 90
        const val MAX_WITHDRAW_PERCENTAGE = 98
        const val DEFAULT_THRESHOLD_SATS = 10000L // 10k sats
        const val DEFAULT_WITHDRAW_PERCENTAGE = 95

        @Volatile
        private var instance: AutoWithdrawSettingsManager? = null

        fun getInstance(context: Context): AutoWithdrawSettingsManager {
            return instance ?: synchronized(this) {
                instance ?: AutoWithdrawSettingsManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    /**
     * Check if auto-withdraw is globally enabled.
     */
    fun isGloballyEnabled(): Boolean = prefs.getBoolean(KEY_GLOBAL_ENABLED, false)

    /**
     * Set global auto-withdraw enabled state.
     */
    fun setGloballyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GLOBAL_ENABLED, enabled).apply()
    }

    /**
     * Get the default threshold in sats.
     */
    fun getDefaultThreshold(): Long = prefs.getLong(KEY_DEFAULT_THRESHOLD, DEFAULT_THRESHOLD_SATS)

    /**
     * Set the default threshold in sats.
     */
    fun setDefaultThreshold(threshold: Long) {
        val clamped = threshold.coerceIn(MIN_THRESHOLD_SATS, MAX_THRESHOLD_SATS)
        prefs.edit().putLong(KEY_DEFAULT_THRESHOLD, clamped).apply()
    }

    /**
     * Get the default withdrawal percentage.
     */
    fun getDefaultPercentage(): Int = prefs.getInt(KEY_DEFAULT_PERCENTAGE, DEFAULT_WITHDRAW_PERCENTAGE)

    /**
     * Set the default withdrawal percentage.
     */
    fun setDefaultPercentage(percentage: Int) {
        val clamped = percentage.coerceIn(MIN_WITHDRAW_PERCENTAGE, MAX_WITHDRAW_PERCENTAGE)
        prefs.edit().putInt(KEY_DEFAULT_PERCENTAGE, clamped).apply()
    }

    /**
     * Get the default lightning address.
     */
    fun getDefaultLightningAddress(): String = prefs.getString(KEY_DEFAULT_LIGHTNING_ADDRESS, "") ?: ""

    /**
     * Set the default lightning address.
     */
    fun setDefaultLightningAddress(address: String) {
        prefs.edit().putString(KEY_DEFAULT_LIGHTNING_ADDRESS, address).apply()
    }

    /**
     * Get settings for a specific mint.
     */
    fun getMintSettings(mintUrl: String): MintWithdrawSettings {
        val allSettings = getAllMintSettings()
        return allSettings[mintUrl] ?: MintWithdrawSettings(
            mintUrl = mintUrl,
            enabled = false,
            thresholdSats = getDefaultThreshold(),
            withdrawPercentage = getDefaultPercentage(),
            lightningAddress = getDefaultLightningAddress()
        )
    }

    /**
     * Save settings for a specific mint.
     */
    fun saveMintSettings(settings: MintWithdrawSettings) {
        val allSettings = getAllMintSettings().toMutableMap()
        allSettings[settings.mintUrl] = settings
        saveMintSettingsMap(allSettings)
    }

    /**
     * Get all mint settings.
     */
    fun getAllMintSettings(): Map<String, MintWithdrawSettings> {
        val json = prefs.getString(KEY_MINT_SETTINGS, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, MintWithdrawSettings>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Save all mint settings.
     */
    private fun saveMintSettingsMap(settings: Map<String, MintWithdrawSettings>) {
        val json = gson.toJson(settings)
        prefs.edit().putString(KEY_MINT_SETTINGS, json).apply()
    }

    /**
     * Check if auto-withdraw is enabled for a specific mint.
     */
    fun isEnabledForMint(mintUrl: String): Boolean {
        if (!isGloballyEnabled()) return false
        return getMintSettings(mintUrl).enabled
    }

    /**
     * Check if a mint balance exceeds the threshold for auto-withdrawal.
     */
    fun shouldTriggerWithdrawal(mintUrl: String, currentBalance: Long): Boolean {
        if (!isEnabledForMint(mintUrl)) return false
        val settings = getMintSettings(mintUrl)
        if (settings.lightningAddress.isBlank()) return false
        return currentBalance >= settings.thresholdSats
    }

    /**
     * Calculate the amount to withdraw based on settings.
     */
    fun calculateWithdrawAmount(mintUrl: String, currentBalance: Long): Long {
        val settings = getMintSettings(mintUrl)
        return (currentBalance * settings.withdrawPercentage / 100)
    }
}
