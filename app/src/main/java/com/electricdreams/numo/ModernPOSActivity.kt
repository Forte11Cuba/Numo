package com.electricdreams.numo
import com.electricdreams.numo.R

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Vibrator
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.electricdreams.numo.core.cashu.CashuWalletManager
import com.electricdreams.numo.core.worker.BitcoinPriceWorker
import com.electricdreams.numo.feature.autowithdraw.AutoWithdrawManager
import com.electricdreams.numo.feature.autowithdraw.AutoWithdrawProgressListener
import com.electricdreams.numo.feature.history.PaymentsHistoryActivity
import com.electricdreams.numo.payment.PaymentMethodHandler
import com.electricdreams.numo.ui.components.PosUiCoordinator

class ModernPOSActivity : AppCompatActivity(), SatocashWallet.OperationFeedback, AutoWithdrawProgressListener {

    private var nfcAdapter: android.nfc.NfcAdapter? = null
    private var bitcoinPriceWorker: BitcoinPriceWorker? = null
    private var vibrator: Vibrator? = null
    
    private lateinit var uiCoordinator: PosUiCoordinator
    private lateinit var autoWithdrawManager: AutoWithdrawManager
    
    // Auto-withdraw progress views
    private lateinit var progressContainer: View
    private lateinit var progressText: android.widget.TextView
    private lateinit var progressAmount: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Load and apply theme settings
        setupThemeSettings()
        
        // Initialize basic setup
        CashuWalletManager.init(this)
        setContentView(R.layout.activity_modern_pos)

        val paymentAmount = intent.getLongExtra("EXTRA_PAYMENT_AMOUNT", 0L)
        Log.d(TAG, "Created ModernPOSActivity with payment amount from basket: $paymentAmount")

        // Setup window settings
        setupWindowSettings()
        
        // Setup Bitcoin price worker
        setupBitcoinPriceWorker()
        
        // Setup NFC
        setupNfcAdapter()

        // Initialize UI coordinator which handles all UI logic
        uiCoordinator = PosUiCoordinator(this, bitcoinPriceWorker)
        uiCoordinator.initialize()

        // Setup auto-withdraw manager and progress indicator
        setupAutoWithdrawProgress()

        // Handle initial payment amount if provided
        uiCoordinator.handleInitialPaymentAmount(paymentAmount)
    }

    private fun setupThemeSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO,
        )
    }

    private fun setupWindowSettings() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, insets.top, 0, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        window.setBackgroundDrawableResource(R.color.color_primary_green)
    }

    private fun setupBitcoinPriceWorker() {
        bitcoinPriceWorker = BitcoinPriceWorker.getInstance(this).also { worker ->
            worker.setPriceUpdateListener(object : BitcoinPriceWorker.PriceUpdateListener {
                override fun onPriceUpdated(price: Double) {
                    // Delegate to UI coordinator when price updates
                    // This will be handled via the display manager
                }
            })
            worker.start()
        }
    }

    private fun setupNfcAdapter() {
        nfcAdapter = android.nfc.NfcAdapter.getDefaultAdapter(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
    }

    private fun setupAutoWithdrawProgress() {
        autoWithdrawManager = AutoWithdrawManager.getInstance(this)
        autoWithdrawManager.setProgressListener(this)
        
        // Initialize progress views
        progressContainer = findViewById<View>(R.id.auto_withdraw_progress_container)
        progressText = findViewById<TextView>(R.id.progress_text)
        progressAmount = findViewById<TextView>(R.id.progress_amount)
    }

    // Lifecycle methods
    override fun onResume() {
        super.onResume()
        
        // Reapply theme when returning from settings
        uiCoordinator.applyTheme()
        
        // Refresh display to update currency formatting when returning from settings
        uiCoordinator.refreshDisplay()
        
        nfcAdapter?.let { adapter ->
            val pendingIntent = PendingIntent.getActivity(
                this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_MUTABLE,
            )
            val techList = arrayOf(arrayOf(android.nfc.tech.IsoDep::class.java.name))
            adapter.enableForegroundDispatch(this, pendingIntent, null, techList)
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onDestroy() {
        uiCoordinator.stopServices()
        bitcoinPriceWorker?.stop()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Dialog layout handled by managers
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (android.nfc.NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            val tag: android.nfc.Tag? = intent.getParcelableExtra(android.nfc.NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                uiCoordinator.handleNfcPayment(tag)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PaymentMethodHandler.REQUEST_CODE_PAYMENT) {
            // Hide spinner regardless of result (success, cancel, or error)
            uiCoordinator.hideChargeButtonSpinner()
            
            // PaymentRequestActivity now handles showing PaymentReceivedActivity directly,
            // so we just need to reset the UI here
            if (resultCode == Activity.RESULT_OK) {
                uiCoordinator.resetToInputMode()
            }
        }
    }

    // Menu handling
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_top_up -> { startActivity(Intent(this, TopUpActivity::class.java)); true }
        R.id.action_balance_check -> { startActivity(Intent(this, BalanceCheckActivity::class.java)); true }
        R.id.action_history -> { startActivity(Intent(this, PaymentsHistoryActivity::class.java)); true }
        else -> super.onOptionsItemSelected(item)
    }

    // SatocashWallet.OperationFeedback implementation
    override fun onOperationSuccess() { 
        runOnUiThread { 
            // Feedback handled by PaymentResultHandler
        } 
    }
    
    override fun onOperationError() { 
        runOnUiThread { 
            // Feedback handled by PaymentResultHandler  
        } 
    }

    // AutoWithdrawProgressListener implementation
    override fun onWithdrawStarted(mintUrl: String, amount: Long, lightningAddress: String) {
        runOnUiThread {
            val amountFormatted = com.electricdreams.numo.core.model.Amount(amount, com.electricdreams.numo.core.model.Amount.Currency.BTC)
            progressText.text = getString(R.string.auto_withdraw_progress_started)
            progressAmount.text = amountFormatted.toString()
            showProgressIndicator()
        }
    }

    override fun onWithdrawProgress(step: String, detail: String) {
        runOnUiThread {
            progressText.text = detail
        }
    }

    override fun onWithdrawCompleted(mintUrl: String, amount: Long, fee: Long) {
        runOnUiThread {
            val amountFormatted = com.electricdreams.numo.core.model.Amount(amount, com.electricdreams.numo.core.model.Amount.Currency.BTC)
            progressText.text = getString(R.string.auto_withdraw_progress_completed)
            progressAmount.text = amountFormatted.toString()
            
            // Hide after 3 seconds
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                hideProgressIndicator()
            }, 3000)
        }
    }

    override fun onWithdrawFailed(mintUrl: String, error: String) {
        runOnUiThread {
            progressText.text = getString(R.string.auto_withdraw_progress_failed)
            progressAmount.text = error
            
            // Hide after 5 seconds (longer for errors)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                hideProgressIndicator()
            }, 5000)
        }
    }

    private fun showProgressIndicator() {
        progressContainer.visibility = View.VISIBLE
        progressContainer.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(300)
            .start()
    }

    private fun hideProgressIndicator() {
        progressContainer.animate()
            .translationY(-progressContainer.height.toFloat())
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                progressContainer.visibility = View.GONE
            }
            .start()
    }

    companion object {
        private const val TAG = "ModernPOSActivity"
        private const val PREFS_NAME = "NumoPrefs"
        private const val KEY_DARK_MODE = "darkMode"
    }
}
