package com.electricdreams.shellshock.feature.settings

import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.electricdreams.shellshock.R
import com.electricdreams.shellshock.core.cashu.CashuWalletManager
import com.electricdreams.shellshock.core.util.MintManager
import com.electricdreams.shellshock.ui.adapter.MintsAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MintsSettingsActivity : AppCompatActivity(), 
    MintsAdapter.MintRemoveListener,
    MintsAdapter.LightningMintSelectedListener {

    companion object {
        private const val TAG = "MintsSettingsActivity"
    }

    private lateinit var mintsRecyclerView: RecyclerView
    private lateinit var mintsAdapter: MintsAdapter
    private lateinit var newMintEditText: EditText
    private lateinit var addMintButton: Button
    private lateinit var resetMintsButton: View
    private lateinit var mintManager: MintManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mints_settings)

        findViewById<View?>(R.id.back_button)?.setOnClickListener { finish() }

        mintManager = MintManager.getInstance(this)

        mintsRecyclerView = findViewById(R.id.mints_recycler_view)
        newMintEditText = findViewById(R.id.new_mint_edit_text)
        addMintButton = findViewById(R.id.add_mint_button)
        resetMintsButton = findViewById(R.id.reset_mints_button)

        mintsRecyclerView.layoutManager = LinearLayoutManager(this)

        mintsAdapter = MintsAdapter(
            this,
            mintManager.getAllowedMints(), 
            this,
            this,
            mintManager.getPreferredLightningMint()
        )
        mintsRecyclerView.adapter = mintsAdapter
        
        // Fetch mint info for all mints (in case not already stored)
        fetchAllMintInfo()

        addMintButton.setOnClickListener { addNewMint() }
        resetMintsButton.setOnClickListener { resetMintsToDefaults() }

        newMintEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addNewMint()
                true
            } else {
                false
            }
        }

        // Load balances for all mints
        loadMintBalances()
    }

    override fun onResume() {
        super.onResume()
        // Refresh balances when returning to the activity
        loadMintBalances()
    }

    private fun loadMintBalances() {
        lifecycleScope.launch {
            val balances = withContext(Dispatchers.IO) {
                CashuWalletManager.getAllMintBalances()
            }
            Log.d(TAG, "Loaded ${balances.size} mint balances")
            mintsAdapter.setAllBalances(balances)
        }
    }

    private fun fetchAllMintInfo() {
        lifecycleScope.launch {
            for (mintUrl in mintManager.getAllowedMints()) {
                // Skip if we already have info for this mint
                if (mintManager.getMintInfo(mintUrl) != null) continue
                
                fetchAndStoreMintInfo(mintUrl)
            }
            // Refresh adapter to show names
            mintsAdapter.notifyDataSetChanged()
        }
    }

    private suspend fun fetchAndStoreMintInfo(mintUrl: String) {
        withContext(Dispatchers.IO) {
            val info = CashuWalletManager.fetchMintInfo(mintUrl)
            if (info != null) {
                val json = CashuWalletManager.mintInfoToJson(info)
                mintManager.setMintInfo(mintUrl, json)
                Log.d(TAG, "Stored mint info for $mintUrl: name=${info.name}")
            } else {
                Log.w(TAG, "Could not fetch mint info for $mintUrl")
            }
        }
    }

    private fun addNewMint() {
        val mintUrl = newMintEditText.text.toString().trim()
        if (TextUtils.isEmpty(mintUrl)) {
            Toast.makeText(this, "Please enter a valid mint URL", Toast.LENGTH_SHORT).show()
            return
        }

        val added = mintManager.addMint(mintUrl)
        if (added) {
            mintsAdapter.updateMints(mintManager.getAllowedMints())
            newMintEditText.setText("")
            // Load balances and fetch mint info for the new mint
            loadMintBalances()
            lifecycleScope.launch {
                fetchAndStoreMintInfo(mintUrl)
                mintsAdapter.notifyDataSetChanged()
            }
        } else {
            Toast.makeText(this, "Mint already in the list", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetMintsToDefaults() {
        mintManager.resetToDefaults()
        mintsAdapter.updateMints(mintManager.getAllowedMints())
        mintsAdapter.setPreferredLightningMint(mintManager.getPreferredLightningMint())
        Toast.makeText(this, "Mints reset to defaults", Toast.LENGTH_SHORT).show()
        // Reload all balances
        loadMintBalances()
    }

    override fun onMintRemoved(mintUrl: String) {
        if (mintManager.removeMint(mintUrl)) {
            mintsAdapter.updateMints(mintManager.getAllowedMints())
            // Update preferred Lightning mint in adapter (may have changed if removed mint was preferred)
            mintsAdapter.setPreferredLightningMint(mintManager.getPreferredLightningMint())
        }
    }

    override fun onLightningMintSelected(mintUrl: String) {
        if (mintManager.setPreferredLightningMint(mintUrl)) {
            mintsAdapter.setPreferredLightningMint(mintUrl)
            Toast.makeText(this, "Lightning payments will use this mint", Toast.LENGTH_SHORT).show()
        }
    }
}
