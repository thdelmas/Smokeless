package com.smokless.smokeless.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.smokless.smokeless.data.entity.SmokingSession
import com.smokless.smokeless.data.entity.Substance
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object DataExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * Export data as CSV
     */
    fun exportAsCSV(
        context: Context,
        sessions: List<SmokingSession>,
    ): File {
        val fileName = "smokeless_export_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)

        file.bufferedWriter().use { writer ->
            // Header: extra columns are positional and default to safe values
            // on import, so older CSVs that lack substance/quantity still parse.
            writer.write("Type,Timestamp,Date,Substance,Quantity\n")

            sessions.forEach { session ->
                val date = dateFormat.format(Date(session.timestamp))
                writer.write(
                    "Smoked,${session.timestamp},\"$date\",${session.substance.name},${session.quantity}\n"
                )
            }
        }

        return file
    }

    /**
     * Export data as JSON
     */
    fun exportAsJSON(
        context: Context,
        sessions: List<SmokingSession>,
    ): File {
        val fileName = "smokeless_export_${System.currentTimeMillis()}.json"
        val file = File(context.cacheDir, fileName)

        val json = JSONObject()
        json.put("export_date", dateFormat.format(Date()))
        json.put("app_version", "2.0")

        val sessionsArray = JSONArray()
        sessions.forEach { session ->
            val sessionObj = JSONObject()
            sessionObj.put("id", session.id)
            sessionObj.put("timestamp", session.timestamp)
            sessionObj.put("date", dateFormat.format(Date(session.timestamp)))
            sessionObj.put("substance", session.substance.name)
            sessionObj.put("quantity", session.quantity)
            sessionsArray.put(sessionObj)
        }
        json.put("smoking_sessions", sessionsArray)

        file.bufferedWriter().use { writer ->
            writer.write(json.toString(2))
        }

        return file
    }

    /**
     * Import data from JSON file. Returns the number of sessions imported.
     * Legacy exports may carry a `cravings_resisted` array — it's silently
     * ignored since the resistance feature is gone.
     */
    fun importFromJSON(
        inputStream: java.io.InputStream,
        sessionDao: com.smokless.smokeless.data.dao.SmokingSessionDao,
    ): Int {
        val content = inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(content)

        var sessionsImported = 0

        if (json.has("smoking_sessions")) {
            val sessionsArray = json.getJSONArray("smoking_sessions")
            for (i in 0 until sessionsArray.length()) {
                val obj = sessionsArray.getJSONObject(i)
                val timestamp = obj.getLong("timestamp")
                val substance = Substance.fromName(obj.optString("substance"))
                val quantity = obj.optDouble("quantity", 1.0)
                sessionDao.insert(SmokingSession(timestamp, substance, quantity))
                sessionsImported++
            }
        }

        return sessionsImported
    }

    /**
     * Import data from CSV file. Returns the number of sessions imported.
     * Legacy "Resisted" rows are silently skipped.
     */
    fun importFromCSV(
        inputStream: java.io.InputStream,
        sessionDao: com.smokless.smokeless.data.dao.SmokingSessionDao,
    ): Int {
        var sessionsImported = 0

        inputStream.bufferedReader().use { reader ->
            // Skip header line
            reader.readLine()

            reader.forEachLine { line ->
                // limit=5 keeps the date column's quoted comma absent — date
                // is "yyyy-MM-dd HH:mm:ss", no internal comma — so a plain
                // split is safe. Extra columns past index 4 are unused.
                val parts = line.split(",", limit = 5)
                if (parts.size >= 2) {
                    val type = parts[0].trim()
                    val timestamp = parts[1].trim().toLongOrNull() ?: return@forEachLine

                    if (type == "Smoked") {
                        val substance = if (parts.size > 3) Substance.fromName(parts[3].trim())
                            else Substance.DEFAULT
                        val quantity = if (parts.size > 4)
                            parts[4].trim().toDoubleOrNull() ?: 1.0
                            else 1.0
                        sessionDao.insert(SmokingSession(timestamp, substance, quantity))
                        sessionsImported++
                    }
                }
            }
        }

        return sessionsImported
    }

    /**
     * Share exported file
     */
    fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = when {
                file.name.endsWith(".csv") -> "text/csv"
                file.name.endsWith(".json") -> "application/json"
                else -> "*/*"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Smokeless Data Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share data via"))
    }
}
