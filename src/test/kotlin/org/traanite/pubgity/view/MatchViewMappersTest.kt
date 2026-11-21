package org.traanite.pubgity.view

import org.junit.jupiter.api.Test
import org.traanite.pubgity.match.Match
import org.traanite.pubgity.match.MatchParticipant
import org.traanite.pubgity.match.MatchParticipantStats
import org.traanite.pubgity.match.ParticipantLifetimeStats
import org.traanite.pubgity.view.aggregation.AggregatedParticipantStats
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatchViewMappersTest {
    private val participant = MatchParticipant(
        accountId = "account.test",
        playerName = "TestPlayer",
        matchStats = MatchParticipantStats(winPlace = 2, kills = 3, damageDealt = 456.9),
        lifetimeStatsSnapshot = ParticipantLifetimeStats(),
        isPrivacyProtected = true
    )

    @Test
    fun `maps every participant field from match and aggregate stats`() {
        val stats = AggregatedParticipantStats(
            totalKills = 10,
            totalDamageDealt = 110.5,
            totalTop10s = 12,
            totalTimeSurvived = 130.5,
            bestRankPoint = 140.5,
            modeWins = 15,
            modeRoundsPlayed = 16,
            modeHeadshotKills = 17,
            modeKD = 18.5,
            dmgPerRoundLifetime = 19.5,
            dmgPerRoundMode = 20.5,
            dmgPerMinuteLifetime = 21.5,
            dmgPerMinuteMode = 22.5
        )
        assertEquals(
            ParticipantView(
                accountId = "account.test",
                playerName = "TestPlayer",
                kills = 10,
                winPlace = 2,
                rosterWinPlace = 4,
                killsMatch = 3,
                dmgMatch = 456,
                damage = 110.5,
                dmgPerRoundLifetime = 19.5,
                dmgPerRoundMode = 20.5,
                dmgPerMinuteLifetime = 21.5,
                dmgPerMinuteMode = 22.5,
                wins = 15,
                roundsPlayed = 16,
                headshotKills = 17,
                top10s = 12,
                timeSurvived = 130.5,
                bestRankPoint = 140.5,
                kd = 18.5,
                isPrivacyProtected = true,
                hasLifetimeStats = true
            ),
            participant.toParticipantView(4, stats)
        )
    }

    @Test
    fun `preserves missing data defaults and snapshot presence independently of aggregate`() {
        val withoutStats = participant.copy(
            matchStats = null, lifetimeStatsSnapshot = null, isPrivacyProtected = false
        )
        val expected = ParticipantView(
            accountId = "account.test",
            playerName = "TestPlayer",
            kills = 0,
            winPlace = 123,
            rosterWinPlace = 4,
            killsMatch = 0,
            dmgMatch = 0,
            damage = 0.0,
            dmgPerRoundLifetime = 0.0,
            dmgPerRoundMode = 0.0,
            dmgPerMinuteLifetime = 0.0,
            dmgPerMinuteMode = 0.0,
            wins = 0,
            roundsPlayed = 0,
            headshotKills = 0,
            top10s = 0,
            timeSurvived = 0.0,
            bestRankPoint = 0.0,
            kd = 0.0,
            isPrivacyProtected = false,
            hasLifetimeStats = false
        )
        assertEquals(expected, withoutStats.toParticipantView(4, null))
        assertEquals(
            expected.copy(
                winPlace = 2, killsMatch = 3, dmgMatch = 456,
                isPrivacyProtected = true, hasLifetimeStats = true
            ),
            participant.toParticipantView(4, null)
        )
        assertFalse(withoutStats.toParticipantView(4, null).hasLifetimeStats)
        assertTrue(participant.toParticipantView(4, null).hasLifetimeStats)
    }

    @Test
    fun `maps detail metadata with readable labels without modifying match`() {
        val mapper = MatchViewMapper(MatchLabelFormatter(JsonMapper.builder().build()))
        val match = Match(
            matchId = "match.test",
            createdAt = Instant.parse("2026-01-01T12:00:00Z"),
            seasonId = null,
            gameMode = "solo-fpp",
            mapName = "Baltic_Main",
            duration = 600,
            botCount = 3,
            telemetryFileUri = null
        )
        assertEquals(
            MatchDetailView(
                matchId = match.matchId,
                createdAt = match.createdAt,
                gameMode = "Solo FPP",
                mapName = "Erangel",
                duration = 600,
                botCount = 3
            ),
            mapper.toDetail(match)
        )
        assertEquals("solo-fpp", match.gameMode)
        assertEquals("Baltic_Main", match.mapName)
    }
}
