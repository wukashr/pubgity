package org.traanite.pubgity.view

import org.bson.types.ObjectId
import org.junit.jupiter.api.Test
import org.traanite.pubgity.match.Match
import org.traanite.pubgity.match.MatchParticipant
import org.traanite.pubgity.match.MatchParticipantStats
import org.traanite.pubgity.view.aggregation.AggregatedParticipantStats
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import kotlin.test.assertEquals

class MatchViewMappersTest {
    private val participant = MatchParticipant(
        accountId = "account.test",
        playerName = "TestPlayer",
        matchStats = MatchParticipantStats(winPlace = 2, kills = 3, damageDealt = 456.9),
        lifetimeStatsSnapshotId = ObjectId(),
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

        val actual = participant.toParticipantView(4, stats)

        val expected = ParticipantView(
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
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `preserves missing data defaults and snapshot presence independently of aggregate`() {
        val withoutStats = participant.copy(
            matchStats = null, lifetimeStatsSnapshotId = null, isPrivacyProtected = false
        )

        val actualWithoutStats = withoutStats.toParticipantView(4, null)
        val actualWithSnapshot = participant.toParticipantView(4, null)

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
        val expectedWithSnapshot = expected.copy(
            winPlace = 2, killsMatch = 3, dmgMatch = 456, isPrivacyProtected = true, hasLifetimeStats = true
        )
        assertEquals(expected, actualWithoutStats)
        assertEquals(expectedWithSnapshot, actualWithSnapshot)
    }

    @Test
    fun `maps detail metadata with readable labels`() {
        val mapper = MatchViewMapper(MatchLabelFormatter(JsonMapper.builder().build()))
        val match = Match(
            matchId = "match.test",
            createdAt = Instant.parse("2026-01-01T12:00:00Z"),
            seasonId = null,
            gameMode = "solo-fpp",
            mapName = "Baltic_Main",
            duration = 1782,
            botCount = 3,
            telemetryFileUri = null
        )

        val actual = mapper.toDetail(match)

        val expected = MatchDetailView(
            matchId = "match.test",
            createdAt = Instant.parse("2026-01-01T12:00:00Z"),
            gameMode = "Solo FPP",
            mapName = "Erangel",
            duration = 1782,
            botCount = 3
        )
        val expectedDuration = "29m 42s"

        assertEquals(expected, actual)
        assertEquals(expectedDuration, actual.durationLabel)
    }

    @Test
    fun `duration labels show minutes and remaining seconds`() {
        val expectedLabels = mapOf(0 to "0m 0s", 59 to "0m 59s", 60 to "1m 0s", 1782 to "29m 42s", 3601 to "60m 1s")

        expectedLabels.forEach { (seconds, expected) -> assertEquals(expected, formatMatchDuration(seconds)) }
    }
}
