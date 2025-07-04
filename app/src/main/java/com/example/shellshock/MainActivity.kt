package com.example.shellshock

import android.R.attr.button
import android.content.BroadcastReceiver
import android.content.Context
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.activity.ComponentActivity

import android.content.Intent;
import android.content.IntentFilter
import android.nfc.cardemulation.HostApduService;
import android.os.Build
import android.widget.TextView

class MainActivity : ComponentActivity() {
    private val TAG = "com.example.shellshock.MainActivity"
    private lateinit var editText: EditText
    private lateinit var button: Button
    private lateinit var textView: TextView

    // BroadcastReceiver to update TextView when the message is updated via NFC write
    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra("message")
            textView.text = "$message"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "OnCreate was called");

        // Find UI components
        editText = findViewById(R.id.editText)
        button = findViewById(R.id.button)
        textView =
            findViewById(R.id.textView) // Add a TextView to display the stored message in the layout

        // Button click listener
        button.setOnClickListener {
            // Set storedMessage to the text from the EditText
            val newMessage = editText.text.toString()
            NfcService.sendMessage = newMessage  // Update the static storedMessage in NfcService
        }

        // Register for the broadcast to update the UI when message is written via NFC
        val filter = IntentFilter("com.example.shellshock.UPDATE_MESSAGE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(messageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        }
    }
}