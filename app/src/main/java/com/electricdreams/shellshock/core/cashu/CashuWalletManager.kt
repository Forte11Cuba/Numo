package com.electricdreams.shellshock.core.cashu

import android.content.Context
import android.util.Log
import com.electricdreams.shellshock.core.util.MintManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.cashudevkit.CurrencyUnit
import org.cashudevkit.MintUrl
import org.cashudevkit.MultiMintWallet
import org.cashudevkit.WalletSqliteDatabase
import org.cashudevkit.generateMnemonic

/**
 * Global owner of the CDK MultiMintWallet and its backing SQLite database.
 *
 * - Initialized from ModernPOSActivity.onCreate().
 * - Re-initialized whenever the allowed mint list changes.
 *
 * The wallet's mnemonic (seed phrase) and SQLite database are both
 * persisted so that balances survive app restarts.
 */
object CashuWalletManager : MintManager.MintChangeListener {

    private const val TAG = "CashuWalletManager"
    private const val PREFS_NAME = "CashuWalletPrefs"
    private const val KEY_MNEMONIC = "wallet_mnemonic"
    private const val DB_FILE_NAME = "cashu_wallet.db"

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var database: WalletSqliteDatabase? = null

    @Volatile
    private var wallet: MultiMintWallet? = null

    /** Initialize from ModernPOSActivity. Safe to call multiple times. */
    fun init(context: Context) {
        if (this::appContext.isInitialized) return

        appContext = context.applicationContext
        val mintManager = MintManager.getInstance(appContext)

        // Listen for changes
        mintManager.setMintChangeListener(this)

        // Build initial wallet
        val initialMints = mintManager.getAllowedMints()
        scope.launch {
            rebuildWallet(initialMints)
        }
    }

    override fun onMintsChanged(newMints: List<String>) {
        Log.d(TAG, "Mint list changed, rebuilding wallet with ${'$'}{newMints.size} mints")
        scope.launch {
            rebuildWallet(newMints)
        }
    }

    /** Current MultiMintWallet instance, or null if initialization failed or not complete. */
    fun getWallet(): MultiMintWallet? = wallet

    /** Current database instance, mostly for debugging or future use. */
    fun getDatabase(): WalletSqliteDatabase? = database

    /**
     * Get the balance for a specific mint in satoshis.
     */
    suspend fun getBalanceForMint(mintUrl: String): Long {
        val w = wallet ?: return 0L
        return try {
            val balanceMap = w.getBalances()
            balanceMap[mintUrl]?.value?.toLong() ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting balance for mint $mintUrl: ${e.message}", e)
            0L
        }
    }

    /**
     * Get balances for all configured mints.
     * Returns a map of mint URL string to balance in satoshis.
     */
    suspend fun getAllMintBalances(): Map<String, Long> {
        val w = wallet ?: return emptyMap()
        return try {
            val balanceMap = w.getBalances()
            balanceMap.mapValues { (_, amount) -> amount.value.toLong() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting mint balances: ${e.message}", e)
            emptyMap()
        }
    }

    /**
     * Fetch mint info from a mint URL using CDK.
     * Returns the MintInfo object, or null if it cannot be fetched.
     */
    suspend fun fetchMintInfo(mintUrl: String): org.cashudevkit.MintInfo? {
        val w = wallet ?: return null
        return try {
            w.fetchMintInfo(MintUrl(mintUrl))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching mint info for $mintUrl: ${e.message}", e)
            null
        }
    }

    /**
     * Convert MintInfo to JSON string for storage.
     */
    fun mintInfoToJson(info: org.cashudevkit.MintInfo): String {
        val json = org.json.JSONObject()
        try {
            info.name?.let { json.put("name", it) }
            info.description?.let { json.put("description", it) }
            info.descriptionLong?.let { json.put("descriptionLong", it) }
            info.pubkey?.let { json.put("pubkey", it) }
            info.version?.let { json.put("version", it) }
            info.motd?.let { json.put("motd", it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error converting mint info to JSON", e)
        }
        return json.toString()
    }

    /**
     * Rebuild wallet + database using the provided mint URLs.
     * Runs on our IO coroutine scope.
     */
    private suspend fun rebuildWallet(mints: List<String>) {
        try {
            // Close any previous instances
            try {
                wallet?.close()
            } catch (t: Throwable) {
                Log.w(TAG, "Error closing previous wallet", t)
            }

            try {
                database?.close()
            } catch (t: Throwable) {
                Log.w(TAG, "Error closing previous DB", t)
            }

            wallet = null
            database = null

            if (mints.isEmpty()) {
                Log.w(TAG, "No allowed mints configured, skipping wallet init")
                return
            }

            // 1) Open or create the on-disk SQLite database.
            val dbFile = appContext.getDatabasePath(DB_FILE_NAME).apply {
                if (!parentFile.exists()) {
                    parentFile.mkdirs()
                }
            }
            val db = WalletSqliteDatabase(dbFile.absolutePath)

            // 2) Load or create the mnemonic (seed phrase).
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var mnemonic = prefs.getString(KEY_MNEMONIC, null)
            if (mnemonic.isNullOrBlank()) {
                mnemonic = generateMnemonic()
                // Persist immediately so the same seed is reused on future launches.
                prefs.edit().putString(KEY_MNEMONIC, mnemonic).apply()
                Log.i(TAG, "Generated and stored new wallet mnemonic")
            } else {
                Log.i(TAG, "Loaded existing wallet mnemonic from preferences")
            }

            // 3) Construct MultiMintWallet in sats.
            val newWallet = MultiMintWallet(
                CurrencyUnit.Sat,
                mnemonic,
                db,
            )

            // 4) Register allowed mints with a default target proof count.
            val targetProofCount: UInt = 10u
            for (url in mints) {
                try {
                    newWallet.addMint(MintUrl(url), targetProofCount)
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to add mint to wallet: ${'$'}url", t)
                }
            }

            database = db
            wallet = newWallet

            Log.d(TAG, "Initialized MultiMintWallet with ${'$'}{mints.size} mints; DB=${'$'}{dbFile.absolutePath}")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize MultiMintWallet", t)
        }
    }
}
