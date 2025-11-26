package com.electricdreams.shellshock.feature.items

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.electricdreams.shellshock.R
import com.electricdreams.shellshock.core.model.Amount
import com.electricdreams.shellshock.core.model.Item
import com.electricdreams.shellshock.core.model.PriceType
import com.electricdreams.shellshock.core.util.CurrencyManager
import com.electricdreams.shellshock.core.util.ItemManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.File
import java.io.IOException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

/**
 * Activity for adding or editing catalog items with an Apple-inspired design.
 * Features:
 * - Dual pricing (Fiat or Bitcoin/sats)
 * - Barcode scanning for SKU
 * - Inventory tracking with low stock alerts
 * - Auto-capitalization for text fields
 */
class ItemEntryActivity : AppCompatActivity() {

    // UI Elements - Basic Info
    private lateinit var nameInput: EditText
    private lateinit var variationInput: EditText
    private lateinit var categoryInput: EditText
    private lateinit var descriptionInput: EditText
    private lateinit var skuInput: EditText

    // UI Elements - Pricing
    private lateinit var priceTypeToggle: MaterialButtonToggleGroup
    private lateinit var btnPriceFiat: MaterialButton
    private lateinit var btnPriceBitcoin: MaterialButton
    private lateinit var fiatPriceContainer: LinearLayout
    private lateinit var satsPriceContainer: LinearLayout
    private lateinit var priceInput: EditText
    private lateinit var satsInput: EditText
    private lateinit var currencySymbol: TextView
    private lateinit var currencyCode: TextView

    // UI Elements - VAT
    private lateinit var vatSectionCard: View
    private lateinit var switchVatEnabled: SwitchMaterial
    private lateinit var vatFieldsContainer: LinearLayout
    private lateinit var switchPriceIncludesVat: SwitchMaterial
    private lateinit var spinnerVatRate: Spinner
    private lateinit var priceBreakdownContainer: LinearLayout
    private lateinit var textNetPrice: TextView
    private lateinit var textVatLabel: TextView
    private lateinit var textVatAmount: TextView
    private lateinit var textGrossPrice: TextView

    // UI Elements - Inventory
    private lateinit var switchTrackInventory: SwitchMaterial
    private lateinit var inventoryFieldsContainer: LinearLayout
    private lateinit var quantityInput: EditText
    private lateinit var alertCheckbox: SwitchMaterial
    private lateinit var alertThresholdContainer: LinearLayout
    private lateinit var alertThresholdInput: EditText

    // UI Elements - Image
    private lateinit var itemImageView: ImageView
    private lateinit var imagePlaceholder: ImageView
    private lateinit var addImageButton: Button
    private lateinit var removeImageButton: Button

    // UI Elements - Actions
    private lateinit var scanBarcodeButton: ImageButton
    private lateinit var cancelButton: Button

    // Managers
    private lateinit var itemManager: ItemManager
    private lateinit var currencyManager: CurrencyManager

    // State
    private var editItemId: String? = null
    private var isEditMode: Boolean = false
    private var selectedImageUri: Uri? = null
    private var currentItem: Item? = null
    private var currentPhotoPath: String? = null
    private var currentPriceType: PriceType = PriceType.FIAT

    // Activity Result Launchers
    private val selectGalleryLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                selectedImageUri = uri
                updateImagePreview()
            }
        }

    private val takePictureLauncher: ActivityResultLauncher<Uri> =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && selectedImageUri != null) {
                updateImagePreview()
            }
        }

    private val barcodeScanLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val barcodeValue = result.data?.getStringExtra(BarcodeScannerActivity.EXTRA_BARCODE_VALUE)
                if (!barcodeValue.isNullOrEmpty()) {
                    skuInput.setText(barcodeValue)
                    Toast.makeText(this, "Barcode scanned successfully", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_entry)

        initializeViews()
        initializeManagers()
        setupPriceTypeToggle()
        setupVatSection()
        setupInventoryTracking()
        setupInputValidation()
        setupClickListeners()

        editItemId = intent.getStringExtra(EXTRA_ITEM_ID)
        isEditMode = !editItemId.isNullOrEmpty()

        if (isEditMode) {
            setupEditMode()
            loadItemData()
        }

        updateCurrencyDisplay()
        updateVatSectionVisibility()
    }

    private fun initializeViews() {
        // Basic Info
        nameInput = findViewById(R.id.item_name_input)
        variationInput = findViewById(R.id.item_variation_input)
        categoryInput = findViewById(R.id.item_category_input)
        descriptionInput = findViewById(R.id.item_description_input)
        skuInput = findViewById(R.id.item_sku_input)

        // Pricing
        priceTypeToggle = findViewById(R.id.price_type_toggle)
        btnPriceFiat = findViewById(R.id.btn_price_fiat)
        btnPriceBitcoin = findViewById(R.id.btn_price_bitcoin)
        fiatPriceContainer = findViewById(R.id.fiat_price_container)
        satsPriceContainer = findViewById(R.id.sats_price_container)
        priceInput = findViewById(R.id.item_price_input)
        satsInput = findViewById(R.id.item_sats_input)
        currencySymbol = findViewById(R.id.currency_symbol)
        currencyCode = findViewById(R.id.currency_code)

        // VAT
        vatSectionCard = findViewById(R.id.vat_section_card)
        switchVatEnabled = findViewById(R.id.switch_vat_enabled)
        vatFieldsContainer = findViewById(R.id.vat_fields_container)
        switchPriceIncludesVat = findViewById(R.id.switch_price_includes_vat)
        spinnerVatRate = findViewById(R.id.spinner_vat_rate)
        priceBreakdownContainer = findViewById(R.id.price_breakdown_container)
        textNetPrice = findViewById(R.id.text_net_price)
        textVatLabel = findViewById(R.id.text_vat_label)
        textVatAmount = findViewById(R.id.text_vat_amount)
        textGrossPrice = findViewById(R.id.text_gross_price)

        // Inventory
        switchTrackInventory = findViewById(R.id.switch_track_inventory)
        inventoryFieldsContainer = findViewById(R.id.inventory_fields_container)
        quantityInput = findViewById(R.id.item_quantity_input)
        alertCheckbox = findViewById(R.id.item_alert_checkbox)
        alertThresholdContainer = findViewById(R.id.alert_threshold_container)
        alertThresholdInput = findViewById(R.id.item_alert_threshold_input)

        // Image
        itemImageView = findViewById(R.id.item_image_view)
        imagePlaceholder = findViewById(R.id.item_image_placeholder)
        addImageButton = findViewById(R.id.item_add_image_button)
        removeImageButton = findViewById(R.id.item_remove_image_button)

        // Actions
        scanBarcodeButton = findViewById(R.id.btn_scan_barcode)
        cancelButton = findViewById(R.id.item_cancel_button)
    }

    private fun initializeManagers() {
        itemManager = ItemManager.getInstance(this)
        currencyManager = CurrencyManager.getInstance(this)
    }

    private fun setupPriceTypeToggle() {
        // Set initial selection
        priceTypeToggle.check(R.id.btn_price_fiat)

        priceTypeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_price_fiat -> {
                        currentPriceType = PriceType.FIAT
                        fiatPriceContainer.visibility = View.VISIBLE
                        satsPriceContainer.visibility = View.GONE
                        updateVatSectionVisibility()
                    }
                    R.id.btn_price_bitcoin -> {
                        currentPriceType = PriceType.SATS
                        fiatPriceContainer.visibility = View.GONE
                        satsPriceContainer.visibility = View.VISIBLE
                        updateVatSectionVisibility()
                    }
                }
            }
        }
    }

    private fun setupVatSection() {
        // Setup VAT rate spinner
        val vatRateLabels = Item.COMMON_VAT_RATES.map { it.first }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, vatRateLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerVatRate.adapter = adapter
        
        // Default to 20% (index 5)
        spinnerVatRate.setSelection(5)

        // VAT enabled toggle
        switchVatEnabled.setOnCheckedChangeListener { _, isChecked ->
            vatFieldsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            priceBreakdownContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            updatePriceBreakdown()
        }

        // Price includes VAT toggle
        switchPriceIncludesVat.setOnCheckedChangeListener { _, _ ->
            updatePriceBreakdown()
        }

        // VAT rate spinner
        spinnerVatRate.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updatePriceBreakdown()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }


    }

    private fun updateVatSectionVisibility() {
        // VAT section only visible for fiat prices
        vatSectionCard.visibility = if (currentPriceType == PriceType.FIAT) View.VISIBLE else View.GONE
        
        // Hide breakdown when VAT section is hidden
        if (currentPriceType != PriceType.FIAT) {
            priceBreakdownContainer.visibility = View.GONE
        } else if (switchVatEnabled.isChecked) {
            priceBreakdownContainer.visibility = View.VISIBLE
        }
    }

    private fun updatePriceBreakdown() {
        if (!switchVatEnabled.isChecked || currentPriceType != PriceType.FIAT) {
            priceBreakdownContainer.visibility = View.GONE
            return
        }

        priceBreakdownContainer.visibility = View.VISIBLE

        // Get entered price
        val priceStr = priceInput.text.toString().trim().replace(",", ".")
        val enteredPrice = priceStr.toDoubleOrNull() ?: 0.0

        // Get selected VAT rate
        val selectedPosition = spinnerVatRate.selectedItemPosition
        val vatRate = Item.COMMON_VAT_RATES.getOrNull(selectedPosition)?.second ?: 0.0

        // Calculate prices based on whether entered price includes VAT or not
        val netPrice: Double
        val grossPrice: Double
        val vatAmount: Double

        if (switchPriceIncludesVat.isChecked) {
            // Entered price is gross (includes VAT) - need to calculate net
            grossPrice = enteredPrice
            netPrice = Item.calculateNetFromGross(grossPrice, vatRate)
            vatAmount = grossPrice - netPrice
        } else {
            // Entered price is net (excludes VAT) - need to calculate gross
            netPrice = enteredPrice
            grossPrice = Item.calculateGrossFromNet(netPrice, vatRate)
            vatAmount = grossPrice - netPrice
        }

        // Format and display
        val currency = Amount.Currency.fromCode(currencyManager.getCurrentCurrency())
        
        textNetPrice.text = Amount.fromMajorUnits(netPrice, currency).toString()
        textVatLabel.text = "VAT (${vatRate.toInt()}%)"
        textVatAmount.text = Amount.fromMajorUnits(vatAmount, currency).toString()
        textGrossPrice.text = Amount.fromMajorUnits(grossPrice, currency).toString()
    }

    private fun getSelectedVatRate(): Double {
        val selectedPosition = spinnerVatRate.selectedItemPosition
        return Item.COMMON_VAT_RATES.getOrNull(selectedPosition)?.second ?: 0.0
    }

    private fun setVatRateByValue(rate: Double) {
        val index = Item.COMMON_VAT_RATES.indexOfFirst { it.second == rate }
        if (index >= 0) {
            spinnerVatRate.setSelection(index)
        }
    }

    private fun setupInventoryTracking() {
        switchTrackInventory.setOnCheckedChangeListener { _, isChecked ->
            inventoryFieldsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        alertCheckbox.setOnCheckedChangeListener { _, isChecked ->
            alertThresholdContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    private fun setupInputValidation() {
        // Fiat price input - allow both . and , as decimal separators, max 2 decimal places
        priceInput.addTextChangedListener(object : TextWatcher {
            private var current = ""
            
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                if (s.toString() != current) {
                    priceInput.removeTextChangedListener(this)
                    
                    val cleanString = s.toString()
                    
                    // Find decimal separator (either . or ,)
                    val decimalSeparator = if (cleanString.contains(",")) "," else "."
                    
                    // Validate decimal places
                    if (cleanString.contains(decimalSeparator)) {
                        val parts = cleanString.split(decimalSeparator)
                        if (parts.size > 1 && parts[1].length > 2) {
                            // Truncate to 2 decimal places
                            val truncated = "${parts[0]}$decimalSeparator${parts[1].substring(0, 2)}"
                            priceInput.setText(truncated)
                            priceInput.setSelection(truncated.length)
                        }
                    }
                    
                    current = priceInput.text.toString()
                    priceInput.addTextChangedListener(this)
                    
                    // Update VAT breakdown in real-time
                    updatePriceBreakdown()
                }
            }
        })

        // Sats input - integers only (already set in XML with inputType="number")
        satsInput.filters = arrayOf(InputFilter { source, start, end, dest, dstart, dend ->
            // Only allow digits
            for (i in start until end) {
                if (!Character.isDigit(source[i])) {
                    return@InputFilter ""
                }
            }
            null
        })
    }

    private fun setupClickListeners() {
        val backButton: View? = findViewById(R.id.back_button)
        val saveButton: Button = findViewById(R.id.item_save_button)

        backButton?.setOnClickListener { finish() }
        saveButton.setOnClickListener { saveItem() }
        addImageButton.setOnClickListener { showImageSourceDialog() }
        // Remove photo button is no longer needed - it's in the dialog now
        removeImageButton.visibility = View.GONE
        scanBarcodeButton.setOnClickListener { launchBarcodeScanner() }

        cancelButton.setOnClickListener {
            if (isEditMode) {
                showDeleteConfirmationDialog()
            } else {
                finish()
            }
        }
    }

    private fun setupEditMode() {
        val toolbarTitle: TextView? = findViewById(R.id.toolbar_title)
        toolbarTitle?.text = "Edit Item"

        cancelButton.text = "Delete Item"
        cancelButton.setTextColor(ContextCompat.getColor(this, R.color.color_warning_red))
    }

    private fun updateCurrencyDisplay() {
        currencySymbol.text = currencyManager.getCurrentSymbol()
        currencyCode.text = currencyManager.getCurrentCurrency()
    }

    private fun loadItemData() {
        for (item in itemManager.getAllItems()) {
            if (item.id == editItemId) {
                currentItem = item

                // Basic info
                nameInput.setText(item.name)
                variationInput.setText(item.variationName)
                categoryInput.setText(item.category)
                descriptionInput.setText(item.description)
                skuInput.setText(item.sku)

                // Pricing
                currentPriceType = item.priceType
                when (item.priceType) {
                    PriceType.FIAT -> {
                        priceTypeToggle.check(R.id.btn_price_fiat)
                        // Show gross price (including VAT) in the input field for user editing
                        val displayPrice = if (item.vatEnabled) item.getGrossPrice() else item.price
                        priceInput.setText(formatFiatPrice(displayPrice))
                        fiatPriceContainer.visibility = View.VISIBLE
                        satsPriceContainer.visibility = View.GONE
                    }
                    PriceType.SATS -> {
                        priceTypeToggle.check(R.id.btn_price_bitcoin)
                        satsInput.setText(item.priceSats.toString())
                        fiatPriceContainer.visibility = View.GONE
                        satsPriceContainer.visibility = View.VISIBLE
                    }
                }

                // VAT
                switchVatEnabled.isChecked = item.vatEnabled
                vatFieldsContainer.visibility = if (item.vatEnabled) View.VISIBLE else View.GONE
                priceBreakdownContainer.visibility = if (item.vatEnabled && item.priceType == PriceType.FIAT) View.VISIBLE else View.GONE
                setVatRateByValue(item.vatRate)
                // When editing, we show gross price in input, so price "includes VAT"
                switchPriceIncludesVat.isChecked = true
                
                // Update VAT section visibility and breakdown
                updateVatSectionVisibility()
                updatePriceBreakdown()

                // Inventory
                switchTrackInventory.isChecked = item.trackInventory
                inventoryFieldsContainer.visibility = if (item.trackInventory) View.VISIBLE else View.GONE
                quantityInput.setText(item.quantity.toString())
                alertCheckbox.isChecked = item.alertEnabled
                alertThresholdContainer.visibility = if (item.alertEnabled) View.VISIBLE else View.GONE
                alertThresholdInput.setText(item.alertThreshold.toString())

                // Image
                if (!item.imagePath.isNullOrEmpty()) {
                    val bitmap = itemManager.loadItemImage(item)
                    if (bitmap != null) {
                        itemImageView.setImageBitmap(bitmap)
                        itemImageView.visibility = View.VISIBLE
                        imagePlaceholder.visibility = View.GONE
                    }
                }
                updatePhotoButtonText()

                break
            }
        }
    }

    private fun formatFiatPrice(price: Double): String {
        // Use Amount class for consistent currency-aware formatting
        val currency = Amount.Currency.fromCode(currencyManager.getCurrentCurrency())
        val minorUnits = Math.round(price * 100)
        val amount = Amount(minorUnits, currency)
        // Return without symbol since the symbol is shown separately in the UI
        return amount.toStringWithoutSymbol()
    }

    private fun launchBarcodeScanner() {
        val intent = Intent(this, BarcodeScannerActivity::class.java)
        barcodeScanLauncher.launch(intent)
    }

    private fun showImageSourceDialog() {
        val hasImage = itemImageView.visibility == View.VISIBLE && imagePlaceholder.visibility == View.GONE
        
        val options = if (hasImage) {
            arrayOf("Take Photo", "Choose from Gallery", "Remove Photo")
        } else {
            arrayOf("Take Photo", "Choose from Gallery")
        }
        
        val title = if (hasImage) "Edit Picture" else "Add Picture"
        
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(options) { _, which ->
                when {
                    which == 0 -> takePicture()
                    which == 1 -> selectFromGallery()
                    which == 2 && hasImage -> removeImage()
                }
            }
            .show()
    }

    private fun selectFromGallery() {
        selectGalleryLauncher.launch("image/*")
    }

    private fun takePicture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_IMAGE_CAPTURE)
            return
        }

        try {
            val photoFile = createImageFile()
            selectedImageUri = FileProvider.getUriForFile(
                this,
                "com.electricdreams.shellshock.fileprovider",
                photoFile,
            )
            takePictureLauncher.launch(selectedImageUri)
        } catch (ex: IOException) {
            Toast.makeText(this, "Error creating image file: ${ex.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val image = File.createTempFile(imageFileName, ".jpg", storageDir)
        currentPhotoPath = image.absolutePath
        return image
    }

    private fun removeImage() {
        selectedImageUri = null
        itemImageView.setImageBitmap(null)
        itemImageView.visibility = View.VISIBLE
        imagePlaceholder.visibility = View.VISIBLE
        updatePhotoButtonText()

        if (isEditMode && currentItem?.imagePath != null) {
            currentItem?.let { itemManager.deleteItemImage(it) }
        }
    }

    private fun updateImagePreview() {
        selectedImageUri?.let { uri ->
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                itemImageView.setImageBitmap(bitmap)
                itemImageView.visibility = View.VISIBLE
                imagePlaceholder.visibility = View.GONE
                updatePhotoButtonText()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updatePhotoButtonText() {
        val hasImage = itemImageView.visibility == View.VISIBLE && imagePlaceholder.visibility == View.GONE
        addImageButton.text = if (hasImage) "Edit Photo" else "Add Photo"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_IMAGE_CAPTURE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                takePicture()
            } else {
                Toast.makeText(this, "Camera permission is required to take pictures", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteConfirmationDialog() {
        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_confirmation, null)
        builder.setView(dialogView)

        val dialog = builder.create()

        val dialogCancelButton: Button = dialogView.findViewById(R.id.dialog_cancel_button)
        val confirmButton: Button = dialogView.findViewById(R.id.dialog_confirm_button)

        dialogCancelButton.setOnClickListener { dialog.dismiss() }

        confirmButton.setOnClickListener {
            currentItem?.let { item ->
                itemManager.removeItem(item.id!!)
                setResult(RESULT_OK)
                dialog.dismiss()
                finish()
            }
        }

        dialog.show()
    }

    private fun saveItem() {
        // Validate name
        val name = nameInput.text.toString().trim()
        if (name.isEmpty()) {
            nameInput.error = "Item name is required"
            nameInput.requestFocus()
            return
        }

        // Validate and get price based on type
        var fiatPrice = 0.0
        var satsPrice = 0L

        when (currentPriceType) {
            PriceType.FIAT -> {
                val priceStr = priceInput.text.toString().trim()
                if (priceStr.isEmpty()) {
                    priceInput.error = "Price is required"
                    priceInput.requestFocus()
                    return
                }
                
                // Normalize decimal separator: replace comma with period for parsing
                val normalizedPriceStr = priceStr.replace(",", ".")
                val enteredPrice = normalizedPriceStr.toDoubleOrNull() ?: 0.0
                if (enteredPrice < 0) {
                    priceInput.error = "Price must be positive"
                    priceInput.requestFocus()
                    return
                }
                
                // Validate max 2 decimal places (accepting both . and , as separators)
                if (!isValidFiatPrice(priceStr)) {
                    priceInput.error = "Maximum 2 decimal places allowed"
                    priceInput.requestFocus()
                    return
                }

                // Convert entered price to net price if VAT is enabled
                val vatEnabled = switchVatEnabled.isChecked
                val priceIncludesVat = switchPriceIncludesVat.isChecked
                val vatRate = getSelectedVatRate()

                fiatPrice = if (vatEnabled && priceIncludesVat) {
                    // Entered price includes VAT - calculate net price
                    Item.calculateNetFromGross(enteredPrice, vatRate)
                } else {
                    // Entered price is net price (or no VAT)
                    enteredPrice
                }
            }
            PriceType.SATS -> {
                val satsStr = satsInput.text.toString().trim()
                if (satsStr.isEmpty()) {
                    satsInput.error = "Price in sats is required"
                    satsInput.requestFocus()
                    return
                }
                
                satsPrice = satsStr.toLongOrNull() ?: 0L
                if (satsPrice < 0) {
                    satsInput.error = "Sats must be positive"
                    satsInput.requestFocus()
                    return
                }
                
                // Validate it's an integer (no decimals)
                if (satsStr.contains(".") || satsStr.contains(",")) {
                    satsInput.error = "Sats must be a whole number"
                    satsInput.requestFocus()
                    return
                }
            }
        }

        // Validate inventory if tracking enabled
        var quantity = 0
        var alertThreshold = 5

        if (switchTrackInventory.isChecked) {
            val quantityStr = quantityInput.text.toString().trim()
            if (quantityStr.isNotEmpty()) {
                quantity = quantityStr.toIntOrNull() ?: 0
                if (quantity < 0) {
                    quantityInput.error = "Quantity must be positive"
                    quantityInput.requestFocus()
                    return
                }
            }

            if (alertCheckbox.isChecked) {
                val thresholdStr = alertThresholdInput.text.toString().trim()
                if (thresholdStr.isNotEmpty()) {
                    alertThreshold = thresholdStr.toIntOrNull() ?: 5
                    if (alertThreshold < 0) {
                        alertThresholdInput.error = "Threshold must be positive"
                        alertThresholdInput.requestFocus()
                        return
                    }
                }
            }
        }

        // Create or update item
        val item = Item().apply {
            if (isEditMode) {
                id = editItemId
                if (currentItem?.imagePath != null && selectedImageUri == null) {
                    imagePath = currentItem?.imagePath
                }
            } else {
                id = UUID.randomUUID().toString()
            }

            this.name = name
            variationName = variationInput.text.toString().trim()
            category = categoryInput.text.toString().trim()
            description = descriptionInput.text.toString().trim()
            sku = skuInput.text.toString().trim()

            // Pricing
            priceType = currentPriceType
            priceCurrency = currencyManager.getCurrentCurrency()
            when (currentPriceType) {
                PriceType.FIAT -> {
                    price = fiatPrice // Always store net price (excluding VAT)
                    priceSats = 0L
                }
                PriceType.SATS -> {
                    price = 0.0
                    priceSats = satsPrice
                }
            }

            // VAT (only for fiat items)
            if (currentPriceType == PriceType.FIAT) {
                vatEnabled = switchVatEnabled.isChecked
                vatRate = if (vatEnabled) getSelectedVatRate() else 0.0
            } else {
                vatEnabled = false
                vatRate = 0.0
            }

            // Inventory
            trackInventory = switchTrackInventory.isChecked
            this.quantity = if (trackInventory) quantity else 0
            alertEnabled = switchTrackInventory.isChecked && alertCheckbox.isChecked
            this.alertThreshold = if (alertEnabled) alertThreshold else 5
        }

        val success = if (isEditMode) {
            itemManager.updateItem(item)
        } else {
            itemManager.addItem(item)
        }

        if (!success) {
            Toast.makeText(this, "Failed to save item", Toast.LENGTH_SHORT).show()
            return
        }

        selectedImageUri?.let { uri ->
            val imageSaved = itemManager.saveItemImage(item, uri)
            if (!imageSaved) {
                Toast.makeText(this, "Item saved but image could not be saved", Toast.LENGTH_LONG).show()
            }
        }

        setResult(RESULT_OK)
        finish()
    }

    private fun isValidFiatPrice(price: String): Boolean {
        // Accept both . and , as decimal separators, max 2 decimal places
        val pattern = Pattern.compile("^\\d+([.,]\\d{0,2})?$")
        return pattern.matcher(price).matches()
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_item_id"
        private const val REQUEST_IMAGE_CAPTURE = 1001
    }
}
