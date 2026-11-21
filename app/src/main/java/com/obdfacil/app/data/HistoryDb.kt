package com.obdfacil.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.obdfacil.app.analysis.Alert
import com.obdfacil.app.obd.LiveData

data class Trip(
    val id: Long,
    val startTs: Long,
    val endTs: Long?,
    val distanceKm: Double,
    val maxSpeed: Int,
    val avgSpeed: Double,
    val maxRpm: Int,
    val maxCoolant: Int?,
    val minVoltage: Double?,
    val maxVoltage: Double?,
    val alerts: Int,
    /** Combustível gasto estimado (litros); null em viagens antigas. */
    val fuelL: Double? = null,
) {
    /** Consumo médio estimado da viagem. */
    val kmPerL: Double? get() = fuelL?.takeIf { it > 0.15 && distanceKm > 0.5 }?.let { distanceKm / it }
    val durationMin: Long get() = ((endTs ?: System.currentTimeMillis()) - startTs) / 60000
}

data class EventRow(val id: Long, val tripId: Long?, val ts: Long, val level: String, val title: String, val message: String)

data class Refuel(
    val id: Long, val ts: Long, val liters: Double, val pricePerL: Double?, val total: Double?,
    val full: Boolean, val fuel: String,
    /** Consumo real medido de tanque cheio a tanque cheio (km/l), quando foi possível calcular. */
    val realKmPerL: Double?,
)

data class ScanRow(val id: Long, val ts: Long, val milOn: Boolean?, val codes: List<String>, val pending: List<String>)

/** Estatísticas que vão sendo somadas durante a viagem. */
class TripStats {
    var distanceKm = 0.0; var maxSpeed = 0; var maxRpm = 0
    var maxCoolant: Int? = null; var minVoltage: Double? = null; var maxVoltage: Double? = null
    var speedSum = 0.0; var speedCount = 0; var alerts = 0
    var fuelL = 0.0
    private var lastTs: Long? = null

    fun add(d: LiveData, maxGapMs: Long = 10_000) {
        val s = d.speedKmh
        lastTs?.let { prev ->
            val gap = d.timestamp - prev
            if (s != null && gap in 0..maxGapMs) distanceKm += s * gap / 3_600_000.0
            d.fuelLh?.let { if (gap in 0..maxGapMs) fuelL += it * gap / 3_600_000.0 }
        }
        lastTs = d.timestamp
        s?.let { maxSpeed = maxOf(maxSpeed, it); if (it > 0) { speedSum += it; speedCount++ } }
        d.rpm?.let { maxRpm = maxOf(maxRpm, it) }
        d.coolantC?.let { maxCoolant = maxOf(maxCoolant ?: it, it) }
        d.voltage?.let { minVoltage = minOf(minVoltage ?: it, it); maxVoltage = maxOf(maxVoltage ?: it, it) }
    }
    val avgSpeed get() = if (speedCount == 0) 0.0 else speedSum / speedCount
}

class HistoryDb(context: Context) : SQLiteOpenHelper(context, "historico.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE trips(id INTEGER PRIMARY KEY AUTOINCREMENT, start_ts INTEGER, end_ts INTEGER,
            distance_km REAL DEFAULT 0, max_speed INTEGER DEFAULT 0, avg_speed REAL DEFAULT 0, max_rpm INTEGER DEFAULT 0,
            max_coolant INTEGER, min_voltage REAL, max_voltage REAL, alerts INTEGER DEFAULT 0, fuel_l REAL)""")
        db.execSQL("""CREATE TABLE samples(trip_id INTEGER, ts INTEGER, rpm INTEGER, speed INTEGER, coolant INTEGER,
            intake INTEGER, load INTEGER, throttle INTEGER, voltage REAL, stft REAL, ltft REAL, map INTEGER, fuel_lh REAL)""")
        db.execSQL("CREATE INDEX idx_samples_trip ON samples(trip_id)")
        db.execSQL("""CREATE TABLE events(id INTEGER PRIMARY KEY AUTOINCREMENT, trip_id INTEGER, ts INTEGER,
            level TEXT, title TEXT, message TEXT)""")
        db.execSQL("""CREATE TABLE scans(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, mil INTEGER,
            codes TEXT, pending TEXT)""")
        createRefuels(db)
    }

    private fun createRefuels(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS refuels(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, liters REAL,
            price_per_l REAL, total REAL, full INTEGER, fuel TEXT, real_kml REAL)""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE trips ADD COLUMN fuel_l REAL")
            db.execSQL("ALTER TABLE samples ADD COLUMN fuel_lh REAL")
            createRefuels(db)
        }
    }

    fun addRefuel(liters: Double, pricePerL: Double?, total: Double?, full: Boolean, fuel: String, realKmPerL: Double?): Long =
        writableDatabase.insert("refuels", null, ContentValues().apply {
            put("ts", System.currentTimeMillis()); put("liters", liters)
            pricePerL?.let { put("price_per_l", it) }; total?.let { put("total", it) }
            put("full", if (full) 1 else 0); put("fuel", fuel); realKmPerL?.let { put("real_kml", it) }
        })

    fun refuels(): List<Refuel> = readableDatabase.rawQuery("SELECT * FROM refuels ORDER BY ts DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(Refuel(c.long("id"), c.long("ts"), c.double("liters"),
            c.doubleOrNull("price_per_l"), c.doubleOrNull("total"), c.int("full") == 1, c.str("fuel"), c.doubleOrNull("real_kml"))) }
    }

    fun deleteRefuel(id: Long) { writableDatabase.delete("refuels", "id=?", arrayOf(id.toString())) }

    fun startTrip(ts: Long): Long = writableDatabase.insert("trips", null, ContentValues().apply { put("start_ts", ts) })

    fun finishTrip(id: Long, s: TripStats, endTs: Long) {
        writableDatabase.update("trips", ContentValues().apply {
            put("end_ts", endTs); put("distance_km", s.distanceKm); put("max_speed", s.maxSpeed)
            put("avg_speed", s.avgSpeed); put("max_rpm", s.maxRpm); put("alerts", s.alerts)
            if (s.fuelL > 0) put("fuel_l", s.fuelL)
            s.maxCoolant?.let { put("max_coolant", it) }
            s.minVoltage?.let { put("min_voltage", it) }
            s.maxVoltage?.let { put("max_voltage", it) }
        }, "id=?", arrayOf(id.toString()))
    }

    fun addSample(tripId: Long, d: LiveData) {
        writableDatabase.insert("samples", null, ContentValues().apply {
            put("trip_id", tripId); put("ts", d.timestamp)
            d.rpm?.let { put("rpm", it) }; d.speedKmh?.let { put("speed", it) }
            d.coolantC?.let { put("coolant", it) }; d.intakeC?.let { put("intake", it) }
            d.loadPct?.let { put("load", it) }; d.throttlePct?.let { put("throttle", it) }
            d.voltage?.let { put("voltage", it) }; d.shortTrimPct?.let { put("stft", it) }
            d.longTrimPct?.let { put("ltft", it) }; d.mapKpa?.let { put("map", it) }
            d.fuelLh?.let { put("fuel_lh", it) }
        })
    }

    fun addEvent(tripId: Long?, a: Alert) {
        writableDatabase.insert("events", null, ContentValues().apply {
            tripId?.let { put("trip_id", it) }
            put("ts", System.currentTimeMillis()); put("level", a.level.name)
            put("title", a.title); put("message", a.message)
        })
    }

    fun addScan(milOn: Boolean?, codes: List<String>, pending: List<String>) {
        writableDatabase.insert("scans", null, ContentValues().apply {
            put("ts", System.currentTimeMillis()); milOn?.let { put("mil", if (it) 1 else 0) }
            put("codes", codes.joinToString(",")); put("pending", pending.joinToString(","))
        })
    }

    fun trips(): List<Trip> = readableDatabase.rawQuery("SELECT * FROM trips ORDER BY start_ts DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(Trip(
            c.long("id"), c.long("start_ts"), c.longOrNull("end_ts"), c.double("distance_km"),
            c.int("max_speed"), c.double("avg_speed"), c.int("max_rpm"), c.intOrNull("max_coolant"),
            c.doubleOrNull("min_voltage"), c.doubleOrNull("max_voltage"), c.int("alerts"), c.doubleOrNull("fuel_l"))) }
    }

    fun samples(tripId: Long): List<LiveData> =
        readableDatabase.rawQuery("SELECT * FROM samples WHERE trip_id=? ORDER BY ts", arrayOf(tripId.toString())).use { c ->
            buildList { while (c.moveToNext()) add(LiveData(
                timestamp = c.long("ts"), rpm = c.intOrNull("rpm"), speedKmh = c.intOrNull("speed"),
                coolantC = c.intOrNull("coolant"), intakeC = c.intOrNull("intake"), loadPct = c.intOrNull("load"),
                throttlePct = c.intOrNull("throttle"), voltage = c.doubleOrNull("voltage"),
                shortTrimPct = c.doubleOrNull("stft"), longTrimPct = c.doubleOrNull("ltft"), mapKpa = c.intOrNull("map"),
                fuelLh = c.doubleOrNull("fuel_lh"))) }
        }

    fun events(tripId: Long? = null): List<EventRow> {
        val (sql, args) = if (tripId == null) "SELECT * FROM events ORDER BY ts DESC LIMIT 500" to null
        else "SELECT * FROM events WHERE trip_id=? ORDER BY ts" to arrayOf(tripId.toString())
        return readableDatabase.rawQuery(sql, args).use { c ->
            buildList { while (c.moveToNext()) add(EventRow(c.long("id"), c.longOrNull("trip_id"), c.long("ts"),
                c.str("level"), c.str("title"), c.str("message"))) }
        }
    }

    fun scans(): List<ScanRow> = readableDatabase.rawQuery("SELECT * FROM scans ORDER BY ts DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(ScanRow(c.long("id"), c.long("ts"),
            c.intOrNull("mil")?.let { it == 1 },
            c.str("codes").split(',').filter { it.isNotBlank() },
            c.str("pending").split(',').filter { it.isNotBlank() })) }
    }

    /**
     * Viagens que ficaram sem resumo (app fechado à força, celular desligado, atualização do app):
     * recalcula o resumo a partir das leituras gravadas. Viagens curtas demais são descartadas.
     */
    fun repairOpenTrips(exceptId: Long? = null) {
        val open = readableDatabase.rawQuery("SELECT id FROM trips WHERE end_ts IS NULL", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }.filter { it != exceptId }
        for (id in open) {
            val samples = samples(id)
            if (samples.size < 5) { deleteTrip(id); continue }
            val stats = TripStats()
            samples.forEach { stats.add(it, maxGapMs = 10_000) }
            stats.alerts = readableDatabase.rawQuery("SELECT COUNT(*) FROM events WHERE trip_id=?", arrayOf(id.toString()))
                .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            finishTrip(id, stats, samples.last().timestamp)
        }
    }

    fun deleteTrip(id: Long) {
        val a = arrayOf(id.toString())
        writableDatabase.delete("samples", "trip_id=?", a)
        writableDatabase.delete("events", "trip_id=?", a)
        writableDatabase.delete("trips", "id=?", a)
    }

    fun clearAll() {
        listOf("samples", "events", "trips", "scans", "refuels").forEach { writableDatabase.delete(it, null, null) }
    }

    private fun Cursor.idx(n: String) = getColumnIndexOrThrow(n)
    private fun Cursor.long(n: String) = getLong(idx(n))
    private fun Cursor.int(n: String) = getInt(idx(n))
    private fun Cursor.double(n: String) = getDouble(idx(n))
    private fun Cursor.str(n: String) = getString(idx(n)) ?: ""
    private fun Cursor.longOrNull(n: String) = idx(n).let { if (isNull(it)) null else getLong(it) }
    private fun Cursor.intOrNull(n: String) = idx(n).let { if (isNull(it)) null else getInt(it) }
    private fun Cursor.doubleOrNull(n: String) = idx(n).let { if (isNull(it)) null else getDouble(it) }
}
