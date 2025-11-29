package com.electricdreams.numo.feature.autowithdraw

import android.content.Context
import android.util.Log
import com.electricdreams.numo.core.cashu.CashuWalletManager
import com.electricdreams.numo.core.data.model.PaymentHistoryEntry
import com.electricdreams.numo.core.util.MintManager
import com.electricdreams.numo.feature.history.PaymentsHistoryActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cashudevkit.MintUrl
import org.cashudevkit.QuoteState
import java.util.Date
import java.util.UUID

/**
 * Data class representing an auto-withdrawal history entry.
 */
data class AutoWithdrawHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val mintUrl: String,
    val lightningAddress: String,
    val amountSats: Long,
    val feeSats: Long,
    val status: String, // "pending", "completed", "failed"
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
    val quoteId: String? = null
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
    }
}

/**
 * Callback interface for auto-withdrawal progress updates.
 */
interface AutoWithdrawProgressListener {
    fun onWithdrawStarted(mintUrl: String, amount: Long, lightningAddress: String)
    fun onWithdrawProgress(step: String, detail: String)
    fun onWithdrawCompleted(mintUrl: String, amount: Long, fee: Long)
    fun onWithdrawFailed(mintUrl: String, error: String)
}

/**
 * Manages automatic withdrawals when mint balances exceed thresholds.
 * 
 * This manager:
 * - Checks if balances exceed configured thresholds after payments
 * - Executes withdrawals to configured Lightning addresses
 * - Persists withdrawal history and melt quotes in payment history
 * - Provides progress callbacks for UI updates
 */
class AutoWithdrawManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AutoWithdrawManager"
        private const val PREFS_NAME = "AutoWithdrawHistory"
        private const val KEY_HISTORY = "history"
        private const val MAX_HISTORY_ENTRIES = 100

        @Volatile
        private var instance: AutoWithdrawManager? = null

        fun getInstance(context: Context): AutoWithdrawManager {
            return instance ?: synchronized(this) {
                instance ?: AutoWithdrawManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val settingsManager = AutoWithdrawSettingsManager.getInstance(context)
    private val mintManager = MintManager.getInstance(context)
    private val gson = Gson()
    
    private var progressListener: AutoWithdrawProgressListener? = null
    
    @Volatile
    private var isWithdrawInProgress = false
    
    /**
     * Application-scoped coroutine scope for background withdrawal operations.
     * Uses SupervisorJob so individual withdrawal failures don't cancel the scope.
     * This scope survives activity lifecycle changes.
     */
    private val withdrawalScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Set a progress listener for UI updates.
     */
    fun setProgressListener(listener: AutoWithdrawProgressListener?) {
        progressListener = listener
    }

    /**
     * Check if a withdrawal is currently in progress.
     */
    fun isWithdrawing(): Boolean = isWithdrawInProgress

    /**
     * Called after a successful payment to check if auto-withdrawal should be triggered.
     * This is the main entry point for triggering auto-withdrawals from payment flows.
     * 
     * This method launches the withdrawal check in a background coroutine that survives
     * activity lifecycle changes. The withdrawal will continue even if the calling
     * activity is destroyed.
     * 
     * @param token The Cashu token received (can be empty for Lightning payments)
     * @param lightningMintUrl The mint URL for Lightning payments (used when token is empty)
     */
    fun onPaymentReceived(token: String, lightningMintUrl: String?) {
        // Determine the mint URL
        val mintUrl: String? = if (token.isNotEmpty()) {
            try {
                com.cashujdk.nut00.Token.decode(token).mint
            } catch (e: Exception) {
                Log.w(TAG, "Could not extract mint URL from token: ${e.message}")
                null
            }
        } else {
            lightningMintUrl
        }
        
        Log.d(TAG, "💰 Payment received, checking for auto-withdrawal. mintUrl=$mintUrl")
        
        if (mintUrl == null) {
            Log.w(TAG, "⚠️ No mint URL available, skipping auto-withdrawal check")
            return
        }
        
        // Launch in application-scoped coroutine that survives activity destruction
        withdrawalScope.launch {
            try {
                Log.d(TAG, "🚀 Starting auto-withdrawal check in background scope")
                checkAndTriggerWithdrawals(mintUrl)
                Log.d(TAG, "✅ Auto-withdrawal check completed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error checking auto-withdrawals", e)
            }
        }
    }

    /**
     * Check all mints and trigger withdrawals if needed.
     * Called after a payment is received.
     * 
     * @param paymentMintUrl Optional: the mint that just received payment (checked first)
     */
    suspend fun checkAndTriggerWithdrawals(paymentMintUrl: String? = null) {
        Log.d(TAG, "=== checkAndTriggerWithdrawals START ===")
        Log.d(TAG, "paymentMintUrl: $paymentMintUrl")
        
        if (isWithdrawInProgress) {
            Log.d(TAG, "Withdrawal already in progress, skipping check")
            return
        }

        if (!settingsManager.isGloballyEnabled()) {
            Log.d(TAG, "Auto-withdraw is globally disabled, skipping")
            return
        }
        
        Log.d(TAG, "Auto-withdraw is globally enabled, checking balances...")

        try {
            // Get all mint balances
            val balances = CashuWalletManager.getAllMintBalances()
            Log.d(TAG, "Retrieved ${balances.size} mint balances: $balances")
            
            if (balances.isEmpty()) {
                Log.w(TAG, "No mint balances found!")
                return
            }
            
            // Check payment mint first if specified
            if (paymentMintUrl != null) {
                Log.d(TAG, "Checking payment mint first: $paymentMintUrl")
                if (balances.containsKey(paymentMintUrl)) {
                    val balance = balances[paymentMintUrl] ?: 0L
                    Log.d(TAG, "Payment mint balance: $balance sats")
                    if (settingsManager.shouldTriggerWithdrawal(paymentMintUrl, balance)) {
                        Log.d(TAG, ">>> Triggering withdrawal for payment mint!")
                        executeWithdrawal(paymentMintUrl, balance)
                        return // Only process one withdrawal at a time
                    } else {
                        Log.d(TAG, "Payment mint did not trigger withdrawal")
                    }
                } else {
                    Log.w(TAG, "Payment mint URL not found in balances! Available mints: ${balances.keys}")
                }
            }

            // Check other mints
            Log.d(TAG, "Checking other mints...")
            for ((mintUrl, balance) in balances) {
                if (mintUrl == paymentMintUrl) {
                    Log.d(TAG, "Skipping $mintUrl (already checked as payment mint)")
                    continue
                }
                Log.d(TAG, "Checking mint: $mintUrl with balance: $balance")
                if (settingsManager.shouldTriggerWithdrawal(mintUrl, balance)) {
                    Log.d(TAG, ">>> Triggering withdrawal for mint: $mintUrl")
                    executeWithdrawal(mintUrl, balance)
                    return // Only process one withdrawal at a time
                }
            }
            
            Log.d(TAG, "No withdrawals triggered for any mint")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking balances for auto-withdraw", e)
        }
        
        Log.d(TAG, "=== checkAndTriggerWithdrawals END ===")
    }

    /**
     * Execute a withdrawal for a specific mint.
     */
    private suspend fun executeWithdrawal(mintUrl: String, currentBalance: Long) {
        if (isWithdrawInProgress) {
            Log.w(TAG, "executeWithdrawal called but already in progress, skipping")
            return
        }
        isWithdrawInProgress = true

        val settings = settingsManager.getMintSettings(mintUrl)
        val withdrawAmount = settingsManager.calculateWithdrawAmount(mintUrl, currentBalance)
        val lightningAddress = settings.lightningAddress

        Log.d(TAG, "🚀 STARTING AUTO-WITHDRAWAL:")
        Log.d(TAG, "   Mint: $mintUrl")
        Log.d(TAG, "   Current balance: $currentBalance sats")
        Log.d(TAG, "   Withdraw amount: $withdrawAmount sats (${settings.withdrawPercentage}%)")
        Log.d(TAG, "   Lightning address: $lightningAddress")
        Log.d(TAG, "   Threshold: ${settings.thresholdSats} sats")

        withContext(Dispatchers.Main) {
            progressListener?.onWithdrawStarted(mintUrl, withdrawAmount, lightningAddress)
            progressListener?.onWithdrawProgress("Preparing", "Getting quote...")
        }

        var historyEntry = AutoWithdrawHistoryEntry(
            mintUrl = mintUrl,
            lightningAddress = lightningAddress,
            amountSats = withdrawAmount,
            feeSats = 0,
            status = AutoWithdrawHistoryEntry.STATUS_PENDING
        )

        try {
            Log.d(TAG, "📋 Step 1: Getting wallet instance...")
            val wallet = CashuWalletManager.getWallet()
            if (wallet == null) {
                throw Exception("Wallet not initialized")
            }
            Log.d(TAG, "✅ Wallet instance obtained")

            // Get melt quote for Lightning address
            Log.d(TAG, "📋 Step 2: Getting melt quote...")
            withContext(Dispatchers.Main) {
                progressListener?.onWithdrawProgress("Quote", "Getting Lightning quote...")
            }

            val amountMsat = withdrawAmount * 1000
            Log.d(TAG, "   Requesting quote for $withdrawAmount sats ($amountMsat msat) to $lightningAddress")
            
            val meltQuote = withContext(Dispatchers.IO) {
                Log.d(TAG, "   Making CDK call: wallet.meltLightningAddressQuote()")
                try {
                    val quote = wallet.meltLightningAddressQuote(MintUrl(mintUrl), lightningAddress, amountMsat.toULong())
                    Log.d(TAG, "   ✅ Quote received: id=${quote.id}")
                    quote
                } catch (e: Exception) {
                    Log.e(TAG, "   ❌ Quote failed: ${e.message}", e)
                    throw e
                }
            }

            val quoteAmount = meltQuote.amount.value.toLong()
            val feeReserve = meltQuote.feeReserve.value.toLong()
            val totalRequired = quoteAmount + feeReserve

            Log.d(TAG, "✅ Melt quote received:")
            Log.d(TAG, "   Quote ID: ${meltQuote.id}")
            Log.d(TAG, "   Amount: $quoteAmount sats")
            Log.d(TAG, "   Fee reserve: $feeReserve sats")
            Log.d(TAG, "   Total required: $totalRequired sats")
            Log.d(TAG, "   Request (BOLT11): ${meltQuote.request}")

            // Check if we have enough balance
            if (totalRequired > currentBalance) {
                Log.e(TAG, "❌ Insufficient balance: need $totalRequired sats, have $currentBalance sats")
                throw Exception("Insufficient balance for withdrawal + fees (need $totalRequired, have $currentBalance)")
            }
            Log.d(TAG, "✅ Balance check passed: $currentBalance >= $totalRequired")

            // Update history entry with quote info
            historyEntry = historyEntry.copy(
                quoteId = meltQuote.id,
                feeSats = feeReserve
            )

            // Create pending payment history entry for the withdrawal
            Log.d(TAG, "📋 Step 3: Creating payment history entry...")
            val paymentHistoryEntry = PaymentHistoryEntry(
                token = "",
                amount = -withdrawAmount, // Negative for outgoing
                date = Date(),
                rawUnit = "sat",
                rawEntryUnit = "sat",
                enteredAmount = withdrawAmount,
                bitcoinPrice = null,
                mintUrl = mintUrl,
                paymentRequest = null,
                rawStatus = PaymentHistoryEntry.STATUS_PENDING,
                paymentType = PaymentHistoryEntry.TYPE_LIGHTNING,
                lightningInvoice = meltQuote.request,
                lightningQuoteId = meltQuote.id,
                lightningMintUrl = mintUrl
            )
            
            withContext(Dispatchers.Main) {
                addPaymentToHistory(paymentHistoryEntry)
            }
            Log.d(TAG, "✅ Payment history entry created: ${paymentHistoryEntry.id}")

            // Execute melt
            Log.d(TAG, "📋 Step 4: Executing melt operation...")
            withContext(Dispatchers.Main) {
                progressListener?.onWithdrawProgress("Sending", "Sending payment...")
            }

            val melted = withContext(Dispatchers.IO) {
                Log.d(TAG, "   Making CDK call: wallet.meltWithMint()")
                try {
                    val result = wallet.meltWithMint(MintUrl(mintUrl), meltQuote.id)
                    Log.d(TAG, "   ✅ Melt completed")
                    result
                } catch (e: Exception) {
                    Log.e(TAG, "   ❌ Melt failed: ${e.message}", e)
                    throw e
                }
            }

            // Check melt state
            Log.d(TAG, "📋 Step 5: Checking final quote state...")
            val finalQuote = withContext(Dispatchers.IO) {
                Log.d(TAG, "   Making CDK call: wallet.checkMeltQuote()")
                try {
                    val quote = wallet.checkMeltQuote(MintUrl(mintUrl), meltQuote.id)
                    Log.d(TAG, "   ✅ Final quote state: ${quote.state}")
                    quote
                } catch (e: Exception) {
                    Log.e(TAG, "   ❌ Quote check failed: ${e.message}", e)
                    throw e
                }
            }

            when (finalQuote.state) {
                QuoteState.PAID -> {
                    Log.d(TAG, "🎉 AUTO-WITHDRAWAL SUCCESSFUL!")
                    Log.d(TAG, "   Amount withdrawn: $withdrawAmount sats")
                    Log.d(TAG, "   Fee paid: $feeReserve sats")
                    Log.d(TAG, "   Lightning address: $lightningAddress")
                    
                    historyEntry = historyEntry.copy(status = AutoWithdrawHistoryEntry.STATUS_COMPLETED)
                    
                    // Update payment history entry
                    withContext(Dispatchers.Main) {
                        updatePaymentHistoryStatus(paymentHistoryEntry.id, PaymentHistoryEntry.STATUS_COMPLETED)
                        progressListener?.onWithdrawCompleted(mintUrl, withdrawAmount, feeReserve)
                    }
                }
                QuoteState.PENDING -> {
                    Log.d(TAG, "⏳ Auto-withdrawal pending (waiting for Lightning payment)")
                    historyEntry = historyEntry.copy(
                        status = AutoWithdrawHistoryEntry.STATUS_PENDING,
                        errorMessage = "Payment pending - check back later"
                    )
                    withContext(Dispatchers.Main) {
                        progressListener?.onWithdrawProgress("Pending", "Payment is pending...")
                    }
                }
                QuoteState.UNPAID -> {
                    Log.e(TAG, "❌ Auto-withdrawal failed: Quote is UNPAID")
                    throw Exception("Payment failed: Quote state is UNPAID")
                }
                else -> {
                    Log.e(TAG, "❌ Auto-withdrawal failed: Unknown quote state ${finalQuote.state}")
                    throw Exception("Payment failed: Unknown quote state ${finalQuote.state}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "💥 AUTO-WITHDRAWAL FAILED:")
            Log.e(TAG, "   Mint: $mintUrl")
            Log.e(TAG, "   Amount: $withdrawAmount sats")
            Log.e(TAG, "   Error: ${e.message}")
            Log.e(TAG, "   Exception type: ${e.javaClass.simpleName}")
            
            // If it's a JobCancellationException, log that specifically
            if (e is kotlinx.coroutines.CancellationException) {
                Log.e(TAG, "   🚫 Withdrawal was cancelled (likely due to scope cancellation)")
            }
            
            historyEntry = historyEntry.copy(
                status = AutoWithdrawHistoryEntry.STATUS_FAILED,
                errorMessage = e.message
            )
            withContext(Dispatchers.Main) {
                progressListener?.onWithdrawFailed(mintUrl, e.message ?: "Unknown error")
            }
        } finally {
            Log.d(TAG, "🏁 Withdrawal finished, saving to history...")
            // Save to auto-withdraw history
            addToHistory(historyEntry)
            isWithdrawInProgress = false
            Log.d(TAG, "🏁 Auto-withdrawal process completed")
        }
    }

    /**
     * Get auto-withdraw history.
     */
    fun getHistory(): List<AutoWithdrawHistoryEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AutoWithdrawHistoryEntry>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading auto-withdraw history", e)
            emptyList()
        }
    }

    /**
     * Add entry to auto-withdraw history.
     */
    private fun addToHistory(entry: AutoWithdrawHistoryEntry) {
        val history = getHistory().toMutableList()
        history.add(0, entry) // Add at beginning (newest first)
        
        // Limit history size
        while (history.size > MAX_HISTORY_ENTRIES) {
            history.removeAt(history.size - 1)
        }
        
        saveHistory(history)
    }

    /**
     * Save auto-withdraw history.
     */
    private fun saveHistory(history: List<AutoWithdrawHistoryEntry>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(history)
        prefs.edit().putString(KEY_HISTORY, json).apply()
    }

    /**
     * Clear auto-withdraw history.
     */
    fun clearHistory() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    /**
     * Add payment entry to payment history.
     */
    private fun addPaymentToHistory(entry: PaymentHistoryEntry) {
        val history = PaymentsHistoryActivity.getPaymentHistory(context).toMutableList()
        history.add(entry)
        val prefs = context.getSharedPreferences("PaymentHistory", Context.MODE_PRIVATE)
        prefs.edit().putString("history", gson.toJson(history)).apply()
    }

    /**
     * Update payment history entry status.
     */
    private fun updatePaymentHistoryStatus(entryId: String, newStatus: String) {
        val history = PaymentsHistoryActivity.getPaymentHistory(context).toMutableList()
        val index = history.indexOfFirst { it.id == entryId }
        if (index >= 0) {
            val existing = history[index]
            val updated = PaymentHistoryEntry(
                id = existing.id,
                token = existing.token,
                amount = existing.amount,
                date = existing.date,
                rawUnit = existing.getUnit(),
                rawEntryUnit = existing.getEntryUnit(),
                enteredAmount = existing.enteredAmount,
                bitcoinPrice = existing.bitcoinPrice,
                mintUrl = existing.mintUrl,
                paymentRequest = existing.paymentRequest,
                rawStatus = newStatus,
                paymentType = existing.paymentType,
                lightningInvoice = existing.lightningInvoice,
                lightningQuoteId = existing.lightningQuoteId,
                lightningMintUrl = existing.lightningMintUrl,
                formattedAmount = existing.formattedAmount,
                checkoutBasketJson = existing.checkoutBasketJson,
                basketId = existing.basketId,
                tipAmountSats = existing.tipAmountSats,
                tipPercentage = existing.tipPercentage
            )
            history[index] = updated
            val prefs = context.getSharedPreferences("PaymentHistory", Context.MODE_PRIVATE)
            prefs.edit().putString("history", gson.toJson(history)).apply()
        }
    }
}
