package org.traanite.pubgity.view

import org.springframework.stereotype.Component
import org.traanite.pubgity.match.Match
import org.traanite.pubgity.match.MatchParticipant
import org.traanite.pubgity.view.aggregation.AggregatedParticipantStats
import java.time.Instant

@Component
class MatchViewMapper(private val labels: MatchLabelFormatter) {
    fun toDetail(match: Match): MatchDetailView = MatchDetailView(
        matchId = match.matchId,
        createdAt = match.createdAt,
        gameMode = labels.gameMode(match.gameMode),
        mapName = labels.mapName(match.mapName),
        duration = match.duration,
        botCount = match.botCount
    )
}

fun MatchParticipant.toParticipantView(
    rosterRank: Int,
    aggregatedStats: AggregatedParticipantStats?
): ParticipantView = ParticipantView(
    accountId = accountId,
    playerName = playerName,
    winPlace = matchStats?.winPlace ?: 123,
    rosterWinPlace = rosterRank,
    killsMatch = matchStats?.kills ?: 0,
    dmgMatch = matchStats?.damageDealt?.toInt() ?: 0,
    kills = aggregatedStats?.totalKills ?: 0,
    damage = aggregatedStats?.totalDamageDealt ?: 0.0,
    dmgPerRoundLifetime = aggregatedStats?.dmgPerRoundLifetime ?: 0.0,
    dmgPerRoundMode = aggregatedStats?.dmgPerRoundMode ?: 0.0,
    dmgPerMinuteLifetime = aggregatedStats?.dmgPerMinuteLifetime ?: 0.0,
    dmgPerMinuteMode = aggregatedStats?.dmgPerMinuteMode ?: 0.0,
    wins = aggregatedStats?.modeWins ?: 0,
    roundsPlayed = aggregatedStats?.modeRoundsPlayed ?: 0,
    headshotKills = aggregatedStats?.modeHeadshotKills ?: 0,
    top10s = aggregatedStats?.totalTop10s ?: 0,
    timeSurvived = aggregatedStats?.totalTimeSurvived ?: 0.0,
    bestRankPoint = aggregatedStats?.bestRankPoint ?: 0.0,
    kd = aggregatedStats?.modeKD ?: 0.0,
    isPrivacyProtected = isPrivacyProtected,
    hasLifetimeStats = lifetimeStatsSnapshot != null
)

data class MatchDetailView(
    val matchId: String,
    val createdAt: Instant,
    val gameMode: String,
    val mapName: String,
    val duration: Int,
    val botCount: Int
)

data class ParticipantView(
    val accountId: String,
    val playerName: String,
    val kills: Int,
    val winPlace: Int,
    val rosterWinPlace: Int,
    val killsMatch: Int,
    val dmgMatch: Int,
    val damage: Double,
    val dmgPerRoundLifetime: Double,
    val dmgPerRoundMode: Double,
    val dmgPerMinuteLifetime: Double,
    val dmgPerMinuteMode: Double,
    val wins: Int,
    val roundsPlayed: Int,
    val headshotKills: Int,
    val top10s: Int,
    val timeSurvived: Double,
    val bestRankPoint: Double,
    val kd: Double,
    val isPrivacyProtected: Boolean = false,
    val hasLifetimeStats: Boolean = true
)
