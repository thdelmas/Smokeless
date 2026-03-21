package com.smokless.smokeless.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.smokless.smokeless.data.entity.Craving
import com.smokless.smokeless.data.entity.SmokingSession
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
        cravings: List<Craving>
    ): File {
        val fileName = "smokeless_export_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        
        file.bufferedWriter().use { writer ->
            // Write header
            writer.write("Type,Timestamp,Date\n")
            
            // Write smoking sessions
            sessions.forEach { session ->
                val date = dateFormat.format(Date(session.timestamp))
                writer.write("Smoked,${session.timestamp},\"$date\"\n")
            }
            
            // Write cravings
            cravings.forEach { craving ->
                val date = dateFormat.format(Date(craving.timestamp))
                writer.write("Resisted,${craving.timestamp},\"$date\"\n")
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
        cravings: List<Craving>
    ): File {
        val fileName = "smokeless_export_${System.currentTimeMillis()}.json"
        val file = File(context.cacheDir, fileName)
        
        val json = JSONObject()
        json.put("export_date", dateFormat.format(Date()))
        json.put("app_version", "2.0")
        
        // Add smoking sessions
        val sessionsArray = JSONArray()
        sessions.forEach { session ->
            val sessionObj = JSONObject()
            sessionObj.put("id", session.id)
            sessionObj.put("timestamp", session.timestamp)
            sessionObj.put("date", dateFormat.format(Date(session.timestamp)))
            sessionsArray.put(sessionObj)
        }
        json.put("smoking_sessions", sessionsArray)
        
        // Add cravings
        val cravingsArray = JSONArray()
        cravings.forEach { craving ->
            val cravingObj = JSONObject()
            cravingObj.put("id", craving.id)
            cravingObj.put("timestamp", craving.timestamp)
            cravingObj.put("date", dateFormat.format(Date(craving.timestamp)))
            cravingsArray.put(cravingObj)
        }
        json.put("cravings_resisted", cravingsArray)
        
        file.bufferedWriter().use { writer ->
            writer.write(json.toString(2))
        }
        
        return file
    }
    
    /**
     * Import data from JSON file
     * Returns pair of (sessions imported, cravings imported)
     */
    fun importFromJSON(
        inputStream: java.io.InputStream,
        sessionDao: com.smokless.smokeless.data.dao.SmokingSessionDao,
        cravingDao: com.smokless.smokeless.data.dao.CravingDao
    ): Pair<Int, Int> {
        val content = inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(content)

        var sessionsImported = 0
        var cravingsImported = 0

        if (json.has("smoking_sessions")) {
            val sessionsArray = json.getJSONArray("smoking_sessions")
            for (i in 0 until sessionsArray.length()) {
                val obj = sessionsArray.getJSONObject(i)
                val timestamp = obj.getLong("timestamp")
                sessionDao.insert(SmokingSession(timestamp))
                sessionsImported++
            }
        }

        if (json.has("cravings_resisted")) {
            val cravingsArray = json.getJSONArray("cravings_resisted")
            for (i in 0 until cravingsArray.length()) {
                val obj = cravingsArray.getJSONObject(i)
                val timestamp = obj.getLong("timestamp")
                cravingDao.insert(Craving(timestamp))
                cravingsImported++
            }
        }

        return Pair(sessionsImported, cravingsImported)
    }

    /**
     * Import data from CSV file
     * Returns pair of (sessions imported, cravings imported)
     */
    fun importFromCSV(
        inputStream: java.io.InputStream,
        sessionDao: com.smokless.smokeless.data.dao.SmokingSessionDao,
        cravingDao: com.smokless.smokeless.data.dao.CravingDao
    ): Pair<Int, Int> {
        var sessionsImported = 0
        var cravingsImported = 0

        inputStream.bufferedReader().use { reader ->
            // Skip header line
            reader.readLine()

            reader.forEachLine { line ->
                val parts = line.split(",", limit = 3)
                if (parts.size >= 2) {
                    val type = parts[0].trim()
                    val timestamp = parts[1].trim().toLongOrNull() ?: return@forEachLine

                    when (type) {
                        "Smoked" -> {
                            sessionDao.insert(SmokingSession(timestamp))
                            sessionsImported++
                        }
                        "Resisted" -> {
                            cravingDao.insert(Craving(timestamp))
                            cravingsImported++
                        }
                    }
                }
            }
        }

        return Pair(sessionsImported, cravingsImported)
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

