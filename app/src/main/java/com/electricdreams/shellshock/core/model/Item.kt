package com.electricdreams.shellshock.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Enum representing the price type for an item.
 */
enum class PriceType {
    FIAT,   // Price in fiat currency (e.g., USD, EUR)
    SATS    // Price in satoshis (Bitcoin)
}

/**
 * Model class for an item in the merchant's catalog.
 *
 * Supports both fiat pricing and Bitcoin (sats) pricing.
 */
@Parcelize
data class Item(
    var id: String? = null,                     // Unique identifier for the item
    var name: String? = null,                   // Item name
    var variationName: String? = null,          // Optional variation name
    var sku: String? = null,                    // Stock keeping unit
    var description: String? = null,            // Item description
    var category: String? = null,               // Category
    var gtin: String? = null,                   // Global Trade Item Number
    var price: Double = 0.0,                    // Price in fiat currency (used when priceType is FIAT)
    var priceSats: Long = 0L,                   // Price in satoshis (used when priceType is SATS)
    var priceType: PriceType = PriceType.FIAT,  // Whether price is in fiat or sats
    var priceCurrency: String = "USD",          // Fiat currency code (USD, EUR, etc.)
    var trackInventory: Boolean = false,        // Whether to track inventory for this item
    var quantity: Int = 0,                      // Available quantity (only used if trackInventory is true)
    var alertEnabled: Boolean = false,          // Whether stock alerts are enabled
    var alertThreshold: Int = 0,                // Threshold for stock alerts
    var imagePath: String? = null,              // Path to item image (can be null)
) : Parcelable {

    /**
     * Get display name combining name and variation if available.
     */
    val displayName: String
        get() = if (!variationName.isNullOrEmpty()) {
            "${name ?: ""} - $variationName"
        } else {
            name.orEmpty()
        }

    /**
     * Get formatted price string based on price type.
     * Uses device locale for number formatting.
     */
    fun getFormattedPrice(currencySymbol: String = "$"): String {
        return when (priceType) {
            PriceType.SATS -> "$priceSats sats"
            PriceType.FIAT -> {
                val format = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.getDefault())
                // We want to use our own currency symbol but locale's number formatting
                val numberFormat = java.text.NumberFormat.getNumberInstance(java.util.Locale.getDefault())
                numberFormat.minimumFractionDigits = 2
                numberFormat.maximumFractionDigits = 2
                "$currencySymbol${numberFormat.format(price)}"
            }
        }
    }

    // Java interop helper for isAlertEnabled() to match original Java API
    fun isAlertEnabled(): Boolean = alertEnabled
    
    // Java interop helper for trackInventory
    fun isTrackInventory(): Boolean = trackInventory
}
