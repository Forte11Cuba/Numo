package com.electricdreams.numo.feature.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.electricdreams.numo.R
import com.electricdreams.numo.feature.items.ItemListActivity
import com.electricdreams.numo.feature.pin.PinEntryActivity
import com.electricdreams.numo.feature.pin.PinManager
import com.electricdreams.numo.feature.pin.PinProtectionHelper
import com.electricdreams.numo.feature.tips.TipsSettingsActivity

/**
 * Main Settings screen.
 * PIN-protected items:
 * - Mints Settings (can withdraw funds)
 * - Items Settings (can modify prices)
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var pinManager: PinManager
    private var pendingDestination: Class<*>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        pinManager = PinManager.getInstance(this)

        findViewById<View?>(R.id.back_button)?.setOnClickListener { finish() }

        // Unprotected settings
        findViewById<View>(R.id.theme_settings_item).setOnClickListener {
            startActivity(Intent(this, ThemeSettingsActivity::class.java))
        }

        findViewById<View>(R.id.currency_settings_item).setOnClickListener {
            startActivity(Intent(this, CurrencySettingsActivity::class.java))
        }

        // Tips settings - unprotected (tips just add to balance)
        findViewById<View>(R.id.tips_settings_item).setOnClickListener {
            startActivity(Intent(this, TipsSettingsActivity::class.java))
        }

        // Security settings - always accessible (contains PIN setup itself)
        findViewById<View>(R.id.security_settings_item).setOnClickListener {
            startActivity(Intent(this, SecuritySettingsActivity::class.java))
        }

        findViewById<View>(R.id.developer_settings_item).setOnClickListener {
            startActivity(Intent(this, DeveloperSettingsActivity::class.java))
        }

        // PIN-protected settings
        findViewById<View>(R.id.mints_settings_item).setOnClickListener {
            openProtectedActivity(MintsSettingsActivity::class.java)
        }

        // Item list - protected because it allows modifying prices
        findViewById<View>(R.id.items_settings_item).setOnClickListener {
            openProtectedActivity(ItemListActivity::class.java)
        }
    }

    private fun openProtectedActivity(destination: Class<*>) {
        if (pinManager.isPinEnabled() && !PinProtectionHelper.isRecentlyVerified()) {
            // Need PIN verification
            pendingDestination = destination
            val intent = Intent(this, PinEntryActivity::class.java).apply {
                putExtra(PinEntryActivity.EXTRA_TITLE, "Enter PIN")
                putExtra(PinEntryActivity.EXTRA_SUBTITLE, "Verify to access settings")
            }
            startActivityForResult(intent, REQUEST_PIN_VERIFY)
        } else {
            // No PIN or recently verified
            startActivity(Intent(this, destination))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_PIN_VERIFY && resultCode == Activity.RESULT_OK) {
            PinProtectionHelper.markVerified()
            pendingDestination?.let { destination ->
                startActivity(Intent(this, destination))
            }
        }
        pendingDestination = null
    }

    companion object {
        private const val REQUEST_PIN_VERIFY = 1001
    }
}
