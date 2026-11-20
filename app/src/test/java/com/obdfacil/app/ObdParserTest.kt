package com.obdfacil.app

import com.obdfacil.app.analysis.DtcDatabase
import com.obdfacil.app.analysis.HealthAnalyzer
import com.obdfacil.app.analysis.Level
import com.obdfacil.app.obd.LiveData
import com.obdfacil.app.obd.ObdParser
import com.obdfacil.app.obd.Pid
import com.obdfacil.app.obd.PidMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdParserTest {

    @Test fun rpmAndCoolant() {
        val rpm = ObdParser.parseMode01("0C", "SEARCHING...\r410C1AF8\r\r>", 2)!!
        assertEquals(1726, PidMath.apply(Pid.RPM, rpm, LiveData()).rpm)
        val t = ObdParser.parseMode01("05", "41 05 7B \r", 1)!!
        assertEquals(83, PidMath.apply(Pid.COOLANT, t, LiveData()).coolantC)
    }

    @Test fun noDataIsNull() {
        assertNull(ObdParser.parseMode01("42", "NO DATA\r", 2))
        assertNull(ObdParser.parseMode01("0C", "UNABLE TO CONNECT\r", 2))
    }

    @Test fun supportedPids() {
        val set = ObdParser.parseSupportedPids(0, "4100BE3EB811")
        assertTrue(0x05 in set); assertTrue(0x0C in set); assertTrue(0x0D in set); assertTrue(0x20 in set)
        assertTrue(0x02 !in set)
    }

    @Test fun dtcKLine() {
        assertEquals(listOf("P0171", "P0420"), ObdParser.parseDtcs("43017104200000\r", isCan = false))
        assertEquals(emptyList<String>(), ObdParser.parseDtcs("43000000000000\r", isCan = false))
    }

    @Test fun dtcCan() {
        assertEquals(listOf("P0133", "P0300"), ObdParser.parseDtcs("430201330300\r", isCan = true))
        // Resposta CAN em vários quadros: C100 -> U0100, 4123 -> C0123, 9234 -> B1234
        assertEquals(listOf("U0100", "C0123", "B1234"),
            ObdParser.parseDtcs("00A\r0:4303C1004123\r1:92340000000000\r", isCan = true))
    }

    @Test fun kLineBusInit() {
        val raw = "BUS INIT: ...OK\r4100BE3EB811\r"
        assertTrue(!ObdParser.isError(raw))
        assertTrue(0x0C in ObdParser.parseSupportedPids(0, raw))
        assertTrue(ObdParser.isError("BUS INIT: ...ERROR\r"))
    }

    @Test fun voltage() {
        assertEquals(12.6, ObdParser.parseVoltage("12.6V\r")!!, 0.001)
    }

    @Test fun overheatIsCritical() {
        val alerts = HealthAnalyzer.analyze(LiveData(coolantC = 112, rpm = 900), emptyList())
        assertEquals(Level.CRITICAL, HealthAnalyzer.overall(alerts))
    }

    @Test fun chargingOk() {
        val alerts = HealthAnalyzer.analyze(LiveData(voltage = 14.1, rpm = 900, coolantC = 90), emptyList())
        assertEquals(Level.OK, HealthAnalyzer.overall(alerts))
    }

    @Test fun trimIgnoredWhenColdOrBrief() {
        val cold = LiveData(rpm = 900, coolantC = 40, shortTrimPct = 10.0, longTrimPct = 20.0)
        assertTrue(HealthAnalyzer.analyze(cold, emptyList(), 60_000).none { it.key == "trim" })
        val warm = cold.copy(coolantC = 88)
        assertEquals(Level.OK, HealthAnalyzer.overall(HealthAnalyzer.analyze(warm, emptyList(), 5_000)))
        assertEquals(Level.WARNING, HealthAnalyzer.overall(HealthAnalyzer.analyze(warm, emptyList(), 31_000)))
    }

    @Test fun misfireIsCritical() {
        val alerts = HealthAnalyzer.analyze(LiveData(), listOf(DtcDatabase.lookup("P0301")))
        assertEquals(Level.CRITICAL, HealthAnalyzer.overall(alerts))
        assertEquals("P1A23", DtcDatabase.lookup("P1A23").code)
    }
}
