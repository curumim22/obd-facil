package com.obdfacil.app.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Uma troca de óleo registrada pelo usuário. */
data class OilChange(
    val ts: Long,
    val km: Double,
    val oilType: String,
    val intervalKm: Int,
    val months: Int,
    val filter: Boolean,
    val airFilter: Boolean = false,
    val fuelFilter: Boolean = false,
) {
    /** "filtro de óleo, de ar e de combustível" (vazio se nenhum). */
    val filtersText: String get() {
        val f = listOfNotNull("óleo".takeIf { filter }, "ar".takeIf { airFilter }, "combustível".takeIf { fuelFilter })
        return when (f.size) {
            0 -> ""
            1 -> "filtro de ${f[0]}"
            else -> "filtros de " + f.dropLast(1).joinToString(", ") + " e " + f.last()
        }
    }
}

enum class OilStatus { UNKNOWN, OK, SOON, DUE }

/** Situação da próxima troca, calculada a partir da última troca e da quilometragem atual. */
data class OilPlan(
    val last: OilChange,
    val nextKm: Double,
    val remainingKm: Double?,   // null se a quilometragem atual não é conhecida
    val dueTs: Long,
    val daysLeft: Long,
    val status: OilStatus,
    val reason: String,         // "km" ou "tempo": o que vence primeiro
) {
    companion object {
        const val SOON_KM = 500.0
        const val SOON_DAYS = 15L
        private const val DAY = 86_400_000L

        fun of(last: OilChange, odometer: Double?, now: Long = System.currentTimeMillis()): OilPlan {
            val nextKm = last.km + last.intervalKm
            val remaining = odometer?.let { nextKm - it }
            val due = addMonths(last.ts, last.months)
            val daysLeft = Math.floorDiv(due - now, DAY)
            val kmDue = remaining != null && remaining <= 0
            val timeDue = last.months > 0 && daysLeft < 0
            val kmSoon = remaining != null && remaining <= SOON_KM
            val timeSoon = last.months > 0 && daysLeft <= SOON_DAYS
            val status = when {
                kmDue || timeDue -> OilStatus.DUE
                kmSoon || timeSoon -> OilStatus.SOON
                else -> OilStatus.OK
            }
            val reason = when {
                kmDue -> "km"; timeDue -> "tempo"; kmSoon -> "km"; timeSoon -> "tempo"; else -> "km"
            }
            return OilPlan(last, nextKm, remaining, due, daysLeft, status, reason)
        }

        fun addMonths(ts: Long, months: Int): Long {
            val c = java.util.Calendar.getInstance().apply { timeInMillis = ts; add(java.util.Calendar.MONTH, months) }
            return c.timeInMillis
        }
    }
}

/** Guarda o histórico de trocas de óleo no aparelho. */
class OilTracker(private val prefs: SharedPreferences) {

    var history: List<OilChange> = load()
        private set

    val last: OilChange? get() = history.maxByOrNull { it.km }

    fun add(c: OilChange) { history = (history + c).sortedByDescending { it.km }; save() }

    fun delete(ts: Long) { history = history.filterNot { it.ts == ts }; save() }

    private fun load(): List<OilChange> = try {
        val arr = JSONArray(prefs.getString("oil_history", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            OilChange(o.getLong("ts"), o.getDouble("km"), o.optString("type"), o.optInt("interval", 5000),
                o.optInt("months", 6), o.optBoolean("filter", false),
                o.optBoolean("air", false), o.optBoolean("fuel", false))
        }.sortedByDescending { it.km }
    } catch (_: Exception) { emptyList() }

    private fun save() {
        val arr = JSONArray()
        history.forEach { c ->
            arr.put(JSONObject().put("ts", c.ts).put("km", c.km).put("type", c.oilType)
                .put("interval", c.intervalKm).put("months", c.months).put("filter", c.filter)
                .put("air", c.airFilter).put("fuel", c.fuelFilter))
        }
        prefs.edit().putString("oil_history", arr.toString()).apply()
    }
}
