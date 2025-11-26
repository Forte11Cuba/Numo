package com.electricdreams.shellshock.feature.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.electricdreams.shellshock.R
import com.electricdreams.shellshock.feature.onboarding.OnboardingActivity

class DeveloperSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_developer_settings)

        findViewById<View?>(R.id.back_button)?.setOnClickListener { finish() }

        findViewById<View>(R.id.restart_onboarding_item).setOnClickListener {
            showRestartOnboardingDialog()
        }
    }

    private fun showRestartOnboardingDialog() {
        AlertDialog.Builder(this)
            .setTitle("Restart Onboarding")
            .setMessage("This will clear your onboarding completion status and take you back to the welcome screen. Are you sure you want to continue?")
            .setPositiveButton("Restart") { _, _ ->
                // Clear onboarding completion status
                OnboardingActivity.setOnboardingComplete(this, false)
                
                // Navigate to onboarding
                val intent = Intent(this, OnboardingActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
