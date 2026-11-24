package com.obdfacil.app

import com.obdfacil.app.data.OilChange
import com.obdfacil.app.data.OilPlan
import com.obdfacil.app.data.OilStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class OilPlanTest {
    private val day = 86_400_000L
    private val now = 1_800_000_000_000L
    private fun change(km: Double, daysAgo: Long = 0, months: Int = 6) =
        OilChange(now - daysAgo * day, km, "5W30", 5000, months, false)

    @Test fun inDay() {
        val p = OilPlan.of(change(245_000.0, 10), 246_000.0, now)
        assertEquals(OilStatus.OK, p.status); assertEquals(4000.0, p.remainingKm!!, 0.1); assertEquals(250_000.0, p.nextKm, 0.1)
    }
    @Test fun soonByKm() = assertEquals(OilStatus.SOON, OilPlan.of(change(245_000.0), 249_600.0, now).status)
    @Test fun dueByKm() {
        val p = OilPlan.of(change(245_000.0), 250_200.0, now)
        assertEquals(OilStatus.DUE, p.status); assertEquals(-200.0, p.remainingKm!!, 0.1)
    }
    @Test fun dueByTime() {
        val p = OilPlan.of(change(245_000.0, 200), 246_000.0, now)
        assertEquals(OilStatus.DUE, p.status); assertEquals("tempo", p.reason)
    }
    @Test fun noMonthLimit() = assertEquals(OilStatus.OK, OilPlan.of(change(245_000.0, 400, months = 0), 246_000.0, now).status)
    @Test fun unknownOdometer() {
        val p = OilPlan.of(change(245_000.0, 10), null, now)
        assertEquals(OilStatus.OK, p.status); assertEquals(null, p.remainingKm)
    }
}

class OilFiltersTextTest {
    private fun c(o: Boolean, a: Boolean, f: Boolean) = OilChange(0, 0.0, "", 5000, 6, o, a, f)
    @Test fun none() = assertEquals("", c(false, false, false).filtersText)
    @Test fun one() = assertEquals("filtro de ar", c(false, true, false).filtersText)
    @Test fun all() = assertEquals("filtros de óleo, ar e combustível", c(true, true, true).filtersText)
}
