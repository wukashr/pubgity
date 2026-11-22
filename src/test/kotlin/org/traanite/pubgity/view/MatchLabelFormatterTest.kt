package org.traanite.pubgity.view

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(OutputCaptureExtension::class)
class MatchLabelFormatterTest {
    private val formatter = MatchLabelFormatter(JsonMapper.builder().build())

    @Test
    fun `maps all PUBG identifiers including both Erangel variants`() {
        val expected = mapOf(
            "Baltic_Main" to "Erangel",
            "Chimera_Main" to "Paramo",
            "Desert_Main" to "Miramar",
            "DihorOtok_Main" to "Vikendi",
            "Erangel_Main" to "Erangel",
            "Heaven_Main" to "Haven",
            "Kiki_Main" to "Deston",
            "Range_Main" to "Camp Jackal",
            "Savage_Main" to "Sanhok",
            "Summerland_Main" to "Karakin",
            "Tiger_Main" to "Taego",
            "Neon_Main" to "Rondo"
        )
        expected.forEach { (raw, label) -> assertEquals(label, formatter.mapName(raw)) }
    }

    @Test
    fun `unknown maps retain their identifier and log a warning`(output: CapturedOutput) {
        val expected = "Future_Main"
        assertEquals(expected, formatter.mapName("Future_Main"))
        assertTrue(output.out.contains("No display name configured for PUBG map 'Future_Main'"))
    }

    @Test
    fun `formats all API game modes and case insensitive FPP`() {
        val expected = mapOf(
            "duo" to "Duo",
            "duo-fpp" to "Duo FPP",
            "solo" to "Solo",
            "solo-fpp" to "Solo FPP",
            "squad" to "Squad",
            "squad-fpp" to "Squad FPP",
            "SOLO-FpP" to "Solo FPP",
            "normal-squad-fpp" to "Normal Squad FPP",
            "custom game-FPP" to "Custom Game FPP",
            "unknown" to "Unknown"
        )
        expected.forEach { (raw, label) -> assertEquals(label, formatter.gameMode(raw)) }
    }
}
