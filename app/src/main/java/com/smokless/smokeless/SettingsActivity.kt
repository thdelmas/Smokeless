package com.smokless.smokeless

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.smokless.smokeless.data.AppDatabase
import com.smokless.smokeless.databinding.ActivitySettingsBinding
import com.smokless.smokeless.ui.settings.SettingsViewModel
import com.smokless.smokeless.util.DataExporter

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: SettingsViewModel by viewModels()

    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        importData(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupStrictMode()
        setupDifficultySlider()
        setupPriceInputs()
        setupExportButtons()
        setupResetButton()
        setupNotificationPrefs()
        setupSocialLinks()
        observeViewModel()
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupStrictMode() {
        binding.switchStrictMode.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setStrictMode(isChecked)
        }
    }
    
    private fun setupDifficultySlider() {
        binding.sliderDifficulty.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.setDifficultyLevel(value.toInt())
            }
        }
    }
    
    private fun setupPriceInputs() {
        binding.editPackPrice.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val price = s?.toString()?.toFloatOrNull()
                if (price != null && price > 0) {
                    viewModel.setPackPrice(price)
                    updateCostPerCig()
                }
            }
        })
        
        binding.editCigsPerPack.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val count = s?.toString()?.toIntOrNull()
                if (count != null && count > 0) {
                    viewModel.setCigsPerPack(count)
                    updateCostPerCig()
                }
            }
        })
    }
    
    private fun updateCostPerCig() {
        val costPerCig = viewModel.getCostPerCigarette()
        val currency = viewModel.currency.value ?: SettingsViewModel.DEFAULT_CURRENCY
        binding.textCostPerCig.text = String.format("%s%.2f per cigarette", currency, costPerCig)
    }
    
    private fun setupExportButtons() {
        binding.btnExportCSV.setOnClickListener {
            exportData("csv")
        }

        binding.btnExportJSON.setOnClickListener {
            exportData("json")
        }

        binding.btnImportData.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Import Data")
                .setMessage("Select a previously exported CSV or JSON file. Imported records will be added to your existing data.")
                .setPositiveButton("Choose File") { _, _ ->
                    importFileLauncher.launch(arrayOf(
                        "application/json",
                        "text/csv",
                        "text/comma-separated-values",
                        "*/*"
                    ))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun exportData(format: String) {
        AppDatabase.databaseExecutor.execute {
            try {
                val db = AppDatabase.getInstance(application)
                val sessions = db.smokingSessionDao().getAllSessions()
                val cravings = db.cravingDao().getAllCravings()
                
                val file = when (format) {
                    "csv" -> DataExporter.exportAsCSV(this, sessions, cravings)
                    "json" -> DataExporter.exportAsJSON(this, sessions, cravings)
                    else -> return@execute
                }
                
                runOnUiThread {
                    DataExporter.shareFile(this, file)
                    Snackbar.make(
                        binding.root,
                        "Data exported successfully!",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Snackbar.make(
                        binding.root,
                        "Export failed: ${e.message}",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    private fun importData(uri: Uri) {
        AppDatabase.databaseExecutor.execute {
            try {
                val db = AppDatabase.getInstance(application)
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw Exception("Could not open file")

                val fileName = uri.lastPathSegment ?: ""
                val (sessions, cravings) = if (fileName.endsWith(".csv")) {
                    DataExporter.importFromCSV(inputStream, db.smokingSessionDao(), db.cravingDao())
                } else {
                    DataExporter.importFromJSON(inputStream, db.smokingSessionDao(), db.cravingDao())
                }

                runOnUiThread {
                    Snackbar.make(
                        binding.root,
                        "Imported $sessions sessions and $cravings cravings",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Snackbar.make(
                        binding.root,
                        "Import failed: ${e.message}",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun setupResetButton() {
        binding.btnResetData.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Reset All Data")
                .setMessage("This will permanently delete all smoking sessions and craving records. This cannot be undone.\n\nAre you sure?")
                .setPositiveButton("Reset") { _, _ ->
                    AppDatabase.databaseExecutor.execute {
                        val db = AppDatabase.getInstance(application)
                        db.smokingSessionDao().deleteAll()
                        db.cravingDao().deleteAll()
                        runOnUiThread {
                            Snackbar.make(binding.root, "All data has been reset", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupNotificationPrefs() {
        val prefs = getSharedPreferences("SmokelessPrefs", MODE_PRIVATE)
        val enabled = prefs.getBoolean("remindersEnabled", true)
        val hour = prefs.getInt("reminderHour", 20)

        binding.switchReminders.isChecked = enabled
        binding.textReminderTime.text = formatHour(hour)
        binding.layoutReminderTime.visibility = if (enabled) android.view.View.VISIBLE else android.view.View.GONE

        binding.switchReminders.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("remindersEnabled", isChecked).apply()
            binding.layoutReminderTime.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
            if (isChecked) {
                com.smokless.smokeless.util.ReminderReceiver.schedule(this)
            } else {
                com.smokless.smokeless.util.ReminderReceiver.cancel(this)
            }
        }

        binding.layoutReminderTime.setOnClickListener {
            val currentHour = prefs.getInt("reminderHour", 20)
            com.google.android.material.timepicker.MaterialTimePicker.Builder()
                .setHour(currentHour)
                .setMinute(0)
                .setTitleText("Reminder Time")
                .build()
                .apply {
                    addOnPositiveButtonClickListener {
                        prefs.edit().putInt("reminderHour", this.hour).apply()
                        binding.textReminderTime.text = formatHour(this.hour)
                        com.smokless.smokeless.util.ReminderReceiver.schedule(this@SettingsActivity)
                    }
                }
                .show(supportFragmentManager, "timePicker")
        }
    }

    private fun formatHour(hour: Int): String {
        return if (hour == 0) "12:00 AM"
        else if (hour < 12) "$hour:00 AM"
        else if (hour == 12) "12:00 PM"
        else "${hour - 12}:00 PM"
    }

    private fun setupSocialLinks() {
        binding.btnGitHub.setOnClickListener {
            openUrl("https://github.com/thdelmas/Smoke-Less")
        }
        
        binding.btnLinkedIn.setOnClickListener {
            openUrl("https://www.linkedin.com/in/th%C3%A9ophile-delmas-92275b16b/")
        }
        
        binding.btnEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:contact@theophile.world")
            }
            startActivity(intent)
        }
    }
    
    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
    
    private fun observeViewModel() {
        viewModel.strictMode.observe(this) { isStrict ->
            if (binding.switchStrictMode.isChecked != isStrict) {
                binding.switchStrictMode.isChecked = isStrict
            }
        }
        
        viewModel.difficultyLevel.observe(this) { level ->
            if (binding.sliderDifficulty.value.toInt() != level) {
                binding.sliderDifficulty.value = level.toFloat()
            }
        }
        
        viewModel.strictModeDescription.observe(this) { desc ->
            binding.textStrictModeDesc.text = desc
        }
        
        viewModel.difficultyDescription.observe(this) { desc ->
            binding.textDifficultyDesc.text = desc
        }
        
        viewModel.packPrice.observe(this) { price ->
            val currentText = binding.editPackPrice.text?.toString()
            if (currentText.isNullOrEmpty() || currentText.toFloatOrNull() != price) {
                binding.editPackPrice.setText(String.format("%.2f", price))
            }
        }
        
        viewModel.cigsPerPack.observe(this) { count ->
            val currentText = binding.editCigsPerPack.text?.toString()
            if (currentText.isNullOrEmpty() || currentText.toIntOrNull() != count) {
                binding.editCigsPerPack.setText(count.toString())
            }
        }
        
        viewModel.currency.observe(this) { _ ->
            updateCostPerCig()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}






