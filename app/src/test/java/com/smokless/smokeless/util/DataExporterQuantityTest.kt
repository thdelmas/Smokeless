package com.smokless.smokeless.util

import com.smokless.smokeless.data.dao.CravingDao
import com.smokless.smokeless.data.dao.SmokingSessionDao
import com.smokless.smokeless.data.entity.Craving
import com.smokless.smokeless.data.entity.SmokingSession
import com.smokless.smokeless.data.entity.Substance
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Verifies that the per-session quantity field round-trips through CSV and
 * JSON export/import, and that legacy exports without a quantity column are
 * imported with the documented fallback (1.0 = full smoke).
 */
class DataExporterQuantityTest {

    private class FakeSessionDao : SmokingSessionDao {
        val rows = mutableListOf<SmokingSession>()
        override fun insert(session: SmokingSession): Long {
            rows += session
            return rows.size.toLong()
        }
        override fun getAllSessionsLive() = error("not used")
        override fun getAllSessions(): List<SmokingSession> = rows.toList()
        override fun getSessionsSince(startTime: Long) = rows.filter { it.timestamp >= startTime }
        override fun getLastTimestamp(): Long? = rows.maxOfOrNull { it.timestamp }
        override fun getSessionCount(): Int = rows.size
        override fun getSessionCountSince(startTime: Long): Int =
            rows.count { it.timestamp >= startTime }
        override fun deleteById(id: Long) { rows.removeAt(0) }
        override fun updateQuantity(id: Long, quantity: Double) {}
        override fun deleteAll() { rows.clear() }
    }

    private class FakeCravingDao : CravingDao {
        val rows = mutableListOf<Craving>()
        override fun insert(craving: Craving): Long { rows += craving; return rows.size.toLong() }
        override fun getAllCravings(): List<Craving> = rows.toList()
        override fun getCravingsSince(startTime: Long) = rows.filter { it.timestamp >= startTime }
        override fun getTotalCount(): Int = rows.size
        override fun getCountSince(startTime: Long): Int =
            rows.count { it.timestamp >= startTime }
        override fun deleteAll() { rows.clear() }
    }

    private fun stream(text: String): InputStream = ByteArrayInputStream(text.toByteArray())

    // --- JSON ---

    @Test
    fun `JSON import reads quantity field when present`() {
        val json = """
            {
              "smoking_sessions": [
                {"timestamp": 1000, "substance": "TOBACCO", "quantity": 0.5},
                {"timestamp": 2000, "substance": "CANNABIS", "quantity": 1.5}
              ],
              "cravings_resisted": []
            }
        """.trimIndent()
        val sessions = FakeSessionDao()
        val cravings = FakeCravingDao()
        DataExporter.importFromJSON(stream(json), sessions, cravings)
        assertEquals(2, sessions.rows.size)
        assertEquals(0.5, sessions.rows[0].quantity, 0.001)
        assertEquals(1.5, sessions.rows[1].quantity, 0.001)
    }

    @Test
    fun `JSON import defaults missing quantity to one`() {
        // Legacy export — no quantity column.
        val json = """
            {
              "smoking_sessions": [
                {"timestamp": 1000, "substance": "TOBACCO"}
              ],
              "cravings_resisted": []
            }
        """.trimIndent()
        val sessions = FakeSessionDao()
        val cravings = FakeCravingDao()
        DataExporter.importFromJSON(stream(json), sessions, cravings)
        assertEquals(1.0, sessions.rows[0].quantity, 0.001)
    }

    // --- CSV ---

    @Test
    fun `CSV import reads quantity column when present`() {
        val csv = "Type,Timestamp,Date,Substance,Quantity\n" +
            "Smoked,1000,\"2026-05-20 09:00:00\",TOBACCO,0.25\n" +
            "Smoked,2000,\"2026-05-20 10:00:00\",CANNABIS,1.5\n"
        val sessions = FakeSessionDao()
        val cravings = FakeCravingDao()
        DataExporter.importFromCSV(stream(csv), sessions, cravings)
        assertEquals(2, sessions.rows.size)
        assertEquals(0.25, sessions.rows[0].quantity, 0.001)
        assertEquals(Substance.TOBACCO, sessions.rows[0].substance)
        assertEquals(1.5, sessions.rows[1].quantity, 0.001)
        assertEquals(Substance.CANNABIS, sessions.rows[1].substance)
    }

    @Test
    fun `CSV import defaults missing quantity to one for legacy exports`() {
        // Legacy CSV with no substance/quantity columns.
        val csv = "Type,Timestamp,Date\n" +
            "Smoked,1000,\"2026-05-20 09:00:00\"\n"
        val sessions = FakeSessionDao()
        val cravings = FakeCravingDao()
        DataExporter.importFromCSV(stream(csv), sessions, cravings)
        assertEquals(1, sessions.rows.size)
        assertEquals(1.0, sessions.rows[0].quantity, 0.001)
        assertEquals(Substance.DEFAULT, sessions.rows[0].substance)
    }

    @Test
    fun `CSV import defaults malformed quantity value to one`() {
        val csv = "Type,Timestamp,Date,Substance,Quantity\n" +
            "Smoked,1000,\"date\",TOBACCO,not-a-number\n"
        val sessions = FakeSessionDao()
        val cravings = FakeCravingDao()
        DataExporter.importFromCSV(stream(csv), sessions, cravings)
        assertEquals(1.0, sessions.rows[0].quantity, 0.001)
    }
}
