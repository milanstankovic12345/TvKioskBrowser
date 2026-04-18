package com.tvappbrowser

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.tvappbrowser.model.AppSettings
import com.tvappbrowser.viewmodel.SettingsViewModel

class SettingsActivity : AppCompatActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    private lateinit var inputUrl: EditText
    private lateinit var layoutIntervalChips: android.widget.LinearLayout
    private lateinit var scrollInterval: android.widget.HorizontalScrollView
    private lateinit var layoutZoomChips: android.widget.LinearLayout
    private lateinit var scrollZoom: android.widget.HorizontalScrollView
    private lateinit var inputPin: EditText
    private var selectedIntervalIndex = 0
    private var selectedZoomIndex = 0
    private var isFormInitialized = false
    
    private lateinit var switchBoot: Switch
    private lateinit var switchKiosk: Switch
    private lateinit var switchJs: Switch
    private lateinit var switchHw: Switch
    private lateinit var switchCache: Switch

    private val presetSeconds = intArrayOf(5, 15, 30, 60, 300, 900, 1200, 1500, 1800, 2700, 3600, 5400, 7200, 9000, 10800, 14400, 21600, 28800, 43200, 86400)
    private val presetStrings = arrayOf("5s", "15s", "30s", "1m", "5m", "15m", "20m", "25m", "30m", "45m", "1h", "1h 30m", "2h", "2h 30m", "3h", "4h", "6h", "8h", "12h", "24h")

    private val presetZoomPercents = intArrayOf(25, 50, 75, 80, 90, 100, 110, 125, 150, 175, 200, 250, 300)
    private val presetZoomStrings = arrayOf("25%", "50%", "75%", "80%", "90%", "100%", "110%", "125%", "150%", "175%", "200%", "250%", "300%")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize UI components
        inputUrl = findViewById(R.id.inputUrl)
        layoutIntervalChips = findViewById(R.id.layoutIntervalChips)
        scrollInterval = findViewById(R.id.scrollInterval)
        layoutZoomChips = findViewById(R.id.layoutZoomChips)
        scrollZoom = findViewById(R.id.scrollZoom)
        inputPin = findViewById(R.id.inputPin)

        // Fix keyboard auto popup on D-pad navigation
        inputUrl.showSoftInputOnFocus = false
        inputUrl.setOnClickListener {
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(inputUrl, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        
        inputPin.showSoftInputOnFocus = false
        inputPin.setOnClickListener {
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(inputPin, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        // Setup rows
        setupRow(R.id.rowBoot, "Start on Boot") { switchBoot = it }
        setupRow(R.id.rowKiosk, "Kiosk Mode") { switchKiosk = it }
        setupRow(R.id.rowJs, "JavaScript Enabled") { switchJs = it }
        setupRow(R.id.rowHw, "Hardware Acceleration") { switchHw = it }
        setupRow(R.id.rowCache, "Clear Cache on Refresh") { switchCache = it }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveAndApply()
        }

        findViewById<Button>(R.id.btnExit).setOnClickListener {
            finishAffinity()
            kotlin.system.exitProcess(0)
        }

        findViewById<Button>(R.id.btnReset).setOnClickListener {
            isFormInitialized = false
            viewModel.resetToDefaults()
        }

        findViewById<Button>(R.id.btnAddBookmark).setOnClickListener {
            showBookmarkDialog(null)
        }

        // Observe settings to populate form
        viewModel.settings.observe(this) { settings ->
            if (!isFormInitialized) {
                populateForm(settings)
                isFormInitialized = true
            } else {
                // Only update bookmarks recycler (don't overwrite switch states)
                updateBookmarksList(settings)
            }
        }
    }

    private fun setupRow(rowId: Int, label: String, onSwitchFound: (Switch) -> Unit) {
        val row = findViewById<android.view.View>(rowId)
        val textLabel = row.findViewById<TextView>(R.id.rowLabel)
        val switchControl = row.findViewById<Switch>(R.id.rowSwitch)
        
        textLabel.text = label
        onSwitchFound(switchControl)
    }

    private fun closestPresetIndex(seconds: Int): Int {
        var minDiff = Int.MAX_VALUE
        var bestIndex = 0
        for (i in presetSeconds.indices) {
            val diff = kotlin.math.abs(presetSeconds[i] - seconds)
            if (diff < minDiff) {
                minDiff = diff
                bestIndex = i
            }
        }
        return bestIndex
    }

    private fun closestZoomIndex(percent: Int): Int {
        var minDiff = Int.MAX_VALUE
        var bestIndex = 5 // default to 100%
        for (i in presetZoomPercents.indices) {
            val diff = kotlin.math.abs(presetZoomPercents[i] - percent)
            if (diff < minDiff) {
                minDiff = diff
                bestIndex = i
            }
        }
        return bestIndex
    }

    private fun populateForm(settings: AppSettings) {
        inputUrl.setText(settings.url)
        
        selectedIntervalIndex = closestPresetIndex(settings.refreshIntervalSec)
        layoutIntervalChips.removeAllViews()
        for (i in presetStrings.indices) {
            val chip = TextView(this)
            chip.text = presetStrings[i]
            chip.isFocusable = true
            chip.isFocusableInTouchMode = true
            chip.isClickable = true
            chip.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_chip)
            chip.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_primary))
            val paddingV = (8 * resources.displayMetrics.density).toInt()
            val paddingH = (16 * resources.displayMetrics.density).toInt()
            chip.setPadding(paddingH, paddingV, paddingH, paddingV)
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, (8 * resources.displayMetrics.density).toInt(), 0)
            chip.layoutParams = params
            
            chip.isSelected = i == selectedIntervalIndex
            
            chip.setOnClickListener {
                selectedIntervalIndex = i
                for (j in 0 until layoutIntervalChips.childCount) {
                    layoutIntervalChips.getChildAt(j).isSelected = j == i
                }
            }
            
            chip.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    // Scroll horizontal view to make this chip visible
                    val left = v.left - scrollInterval.paddingLeft
                    val right = v.right + scrollInterval.paddingRight
                    scrollInterval.requestChildRectangleOnScreen(layoutIntervalChips, android.graphics.Rect(left, 0, right, v.height), true)
                }
            }
            
            layoutIntervalChips.addView(chip)
        }
        
        // Scroll to initial selection after layout pass
        scrollInterval.post {
            val selectedChip = layoutIntervalChips.getChildAt(selectedIntervalIndex)
            if (selectedChip != null) {
                val left = selectedChip.left - scrollInterval.paddingLeft
                val right = selectedChip.right + scrollInterval.paddingRight
                scrollInterval.requestChildRectangleOnScreen(layoutIntervalChips, android.graphics.Rect(left, 0, right, selectedChip.height), true)
            }
        }

        switchBoot.isChecked = settings.startOnBoot
        switchKiosk.isChecked = settings.kioskMode
        switchJs.isChecked = settings.javascriptEnabled
        switchHw.isChecked = settings.hardwareAccel
        switchCache.isChecked = settings.clearCache

        // Zoom level chips (same pattern as interval chips)
        selectedZoomIndex = closestZoomIndex(settings.zoomLevel)
        layoutZoomChips.removeAllViews()
        for (i in presetZoomStrings.indices) {
            val chip = TextView(this)
            chip.text = presetZoomStrings[i]
            chip.isFocusable = true
            chip.isFocusableInTouchMode = true
            chip.isClickable = true
            chip.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_chip)
            chip.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_primary))
            val paddingV = (8 * resources.displayMetrics.density).toInt()
            val paddingH = (16 * resources.displayMetrics.density).toInt()
            chip.setPadding(paddingH, paddingV, paddingH, paddingV)
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, (8 * resources.displayMetrics.density).toInt(), 0)
            chip.layoutParams = params

            chip.isSelected = i == selectedZoomIndex

            chip.setOnClickListener {
                selectedZoomIndex = i
                for (j in 0 until layoutZoomChips.childCount) {
                    layoutZoomChips.getChildAt(j).isSelected = j == i
                }
            }

            chip.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    val left = v.left - scrollZoom.paddingLeft
                    val right = v.right + scrollZoom.paddingRight
                    scrollZoom.requestChildRectangleOnScreen(layoutZoomChips, android.graphics.Rect(left, 0, right, v.height), true)
                }
            }

            layoutZoomChips.addView(chip)
        }

        // Scroll to initial zoom selection after layout pass
        scrollZoom.post {
            val selectedChip = layoutZoomChips.getChildAt(selectedZoomIndex)
            if (selectedChip != null) {
                val left = selectedChip.left - scrollZoom.paddingLeft
                val right = selectedChip.right + scrollZoom.paddingRight
                scrollZoom.requestChildRectangleOnScreen(layoutZoomChips, android.graphics.Rect(left, 0, right, selectedChip.height), true)
            }
        }
        
        inputPin.setText(settings.pinCode)

        // Update bookmarks Recycler
        updateBookmarksList(settings)
    }

    private fun updateBookmarksList(settings: AppSettings) {
        val recycler = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerBookmarks)
        recycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
        recycler.adapter = BookmarkAdapter(settings.bookmarks, { bookmark ->
            // Play bookmark (update URL and exit settings)
            val currentSettings = viewModel.settings.value
            if (currentSettings != null) {
                var urlToLoad = bookmark.url
                if (!urlToLoad.startsWith("http")) urlToLoad = "https://$urlToLoad"
                inputUrl.setText(urlToLoad)
                saveAndApply()
            }
        }, { bookmark ->
            // Edit
            showBookmarkDialog(bookmark)
        }, { bookmark ->
            // Delete
            val currentBookmarks = viewModel.settings.value?.bookmarks?.toMutableList() ?: mutableListOf()
            currentBookmarks.removeAll { it.id == bookmark.id }
            val newSettings = viewModel.settings.value?.copy(bookmarks = currentBookmarks) ?: AppSettings()
            viewModel.saveSettings(newSettings)
        })
    }

    private fun showBookmarkDialog(existingBookmark: com.tvappbrowser.model.Bookmark?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_bookmark_entry, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setView(dialogView)
            .create()
            
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val inputName = dialogView.findViewById<EditText>(R.id.inputBookmarkName)
        val inputUrlDialog = dialogView.findViewById<EditText>(R.id.inputBookmarkUrl)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveBookmark)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnDelete = dialogView.findViewById<Button>(R.id.btnDelete)
        val textTitle = dialogView.findViewById<TextView>(R.id.textDialogTitle)

        inputName.showSoftInputOnFocus = false
        inputUrlDialog.showSoftInputOnFocus = false
        
        val showKeyboard = android.view.View.OnClickListener { v ->
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(v, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        inputName.setOnClickListener(showKeyboard)
        inputUrlDialog.setOnClickListener(showKeyboard)

        if (existingBookmark != null) {
            textTitle.text = "Edit Bookmark"
            inputName.setText(existingBookmark.name)
            inputUrlDialog.setText(existingBookmark.url)
            btnDelete.visibility = android.view.View.VISIBLE
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnDelete.setOnClickListener {
            val currentBookmarks = viewModel.settings.value?.bookmarks?.toMutableList() ?: mutableListOf()
            currentBookmarks.removeAll { it.id == existingBookmark?.id }
            val newSettings = viewModel.settings.value?.copy(bookmarks = currentBookmarks) ?: AppSettings()
            viewModel.saveSettings(newSettings)
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val name = inputName.text.toString().trim()
            val url = inputUrlDialog.text.toString().trim()
            if (name.isNotEmpty() && url.isNotEmpty()) {
                val currentBookmarks = viewModel.settings.value?.bookmarks?.toMutableList() ?: mutableListOf()
                if (existingBookmark != null) {
                    val index = currentBookmarks.indexOfFirst { it.id == existingBookmark.id }
                    if (index != -1) {
                        currentBookmarks[index] = com.tvappbrowser.model.Bookmark(existingBookmark.id, name, url)
                    }
                } else {
                    currentBookmarks.add(com.tvappbrowser.model.Bookmark(java.util.UUID.randomUUID().toString(), name, url))
                }
                val newSettings = viewModel.settings.value?.copy(bookmarks = currentBookmarks) ?: AppSettings()
                viewModel.saveSettings(newSettings)
                dialog.dismiss()
            } else {
                android.widget.Toast.makeText(this, "Please enter both Name and URL", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun saveAndApply() {
        val newSettings = AppSettings(
            url = inputUrl.text.toString(),
            refreshIntervalSec = presetSeconds[selectedIntervalIndex],
            startOnBoot = switchBoot.isChecked,
            kioskMode = switchKiosk.isChecked,
            javascriptEnabled = switchJs.isChecked,
            hardwareAccel = switchHw.isChecked,
            clearCache = switchCache.isChecked,
            zoomLevel = presetZoomPercents[selectedZoomIndex],
            pinCode = inputPin.text.toString().ifEmpty { "1234" },
            bookmarks = viewModel.settings.value?.bookmarks ?: emptyList()
        )
        viewModel.saveSettings(newSettings)
        
        // Exit settings
        finish()
    }
}
