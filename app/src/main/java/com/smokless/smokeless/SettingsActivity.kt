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
import com.smokless.smokeless.bios.BiosClient
import com.smokless.smokeless.data.AppDatabase
import com.smokless.smokeless.data.repository.SmokingRepository
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
        setupBiosIntegration()
        setupExportButtons()
        setupResetButton()
        setupNotificationPrefs()
        setupSocialLinks()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshBiosStatus()
    }

    private fun setupBiosIntegration() {
        binding.switchBiosIntegration.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setBiosEnabled(isChecked)
        }
        binding.btnSyncBiosHistory.setOnClickListener {
            confirmBackfill()
        }
        binding.btnOpenBiosCompanions.setOnClickListener {
            launchBiosCompanions()
        }
    }

    /**
     * Launches Bios's main activity with the deep-link extra it reads to
     * jump straight into Settings → Companion Apps. Used by the pending-
     * approval banner so the user is one tap from approving Smokeless.
     */
    private fun launchBiosCompanions() {
        val intent = packageManager.getLaunchIntentForPackage(BiosClient.BIOS_PACKAGE)
        if (intent == null) {
            Snackbar.make(
                binding.root,
                "Bios isn't installed on this device.",
                Snackbar.LENGTH_SHORT,
            ).show()
            return
        }
        intent.putExtra(BiosClient.BIOS_EXTRA_NAVIGATE_TO_COMPANIONS, true)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Snackbar.make(
                binding.root,
                "Couldn't open Bios — try opening it manually and check Settings → Companion Apps.",
                Snackbar.LENGTH_LONG,
            ).show()
        }
    }

    private fun confirmBackfill() {
        if (viewModel.biosStatus.value != BiosClient.Status.CONNECTED) {
            Snackbar.make(
                binding.root,
                "Enable Bios integration first.",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Sync history to Bios")
            .setMessage(
                "Push every existing smoking and craving record to Bios so it can " +
                "correlate them with your past health metrics.\n\n" +
                "Running this more than once may create duplicate events in Bios."
            )
            .setPositiveButton("Sync") { _, _ -> runBackfill() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runBackfill() {
        binding.btnSyncBiosHistory.isEnabled = false
        AppDatabase.databaseExecutor.execute {
            val repository = SmokingRepository(application)
            val result = repository.backfillBios()
            runOnUiThread {
                binding.btnSyncBiosHistory.isEnabled = true
                val message = when {
                    result.total == 0 -> "No history to sync."
                    result.failed == 0 -> "Pushed ${result.pushed} events to Bios."
                    else -> "Pushed ${result.pushed}/${result.total} events (${result.failed} failed)."
                }
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
            }
        }
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

        binding.switchTriggerHeadsUp.isChecked =
            com.smokless.smokeless.util.TriggerWindowReceiver.isEnabled(this)
        binding.switchTriggerHeadsUp.setOnCheckedChangeListener { _, isChecked ->
            com.smokless.smokeless.util.TriggerWindowReceiver.setEnabled(this, isChecked)
        }

        binding.switchWeeklyDigest.isChecked =
            com.smokless.smokeless.util.WeeklyDigestReceiver.isEnabled(this)
        binding.switchWeeklyDigest.setOnCheckedChangeListener { _, isChecked ->
            com.smokless.smokeless.util.WeeklyDigestReceiver.setEnabled(this, isChecked)
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

        viewModel.biosEnabled.observe(this) { enabled ->
            if (binding.switchBiosIntegration.isChecked != enabled) {
                binding.switchBiosIntegration.isChecked = enabled
            }
            val available = viewModel.biosStatus.value != BiosClient.Status.NOT_INSTALLED
            binding.switchBiosIntegration.isEnabled = available
        }

        viewModel.biosStatus.observe(this) { status ->
            updateBiosStatusViews(status, viewModel.biosLastPushOutcome.value)
        }

        viewModel.biosLastPushOutcome.observe(this) { outcome ->
            updateBiosStatusViews(viewModel.biosStatus.value, outcome)
        }
    }

    /**
     * Joint render of [BiosClient.Status] + [BiosClient.LastPushOutcome].
     * Status alone says whether the integration is wired; the outcome
     * adds the post-write state — specifically the *pending approval*
     * case, which is the most common reason an enabled integration
     * isn't actually flowing data.
     */
    private fun updateBiosStatusViews(
        status: BiosClient.Status?,
        outcome: BiosClient.LastPushOutcome?,
    ) {
        val pending = status == BiosClient.Status.CONNECTED &&
            outcome == BiosClient.LastPushOutcome.PENDING_APPROVAL
        binding.textBiosStatus.text = when {
            pending -> "Waiting for approval in Bios"
            status == BiosClient.Status.CONNECTED &&
                outcome == BiosClient.LastPushOutcome.OK -> "Connected to Bios"
            status == BiosClient.Status.CONNECTED -> "Connected to Bios"
            status == BiosClient.Status.NOT_ENABLED -> "Not enabled"
            else -> "Bios not installed"
        }
        binding.switchBiosIntegration.isEnabled = status == BiosClient.Status.CONNECTED ||
            status == BiosClient.Status.NOT_ENABLED
        binding.btnSyncBiosHistory.isEnabled = status == BiosClient.Status.CONNECTED
        binding.biosPendingApprovalBanner.visibility =
            if (pending) android.view.View.VISIBLE else android.view.View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}






