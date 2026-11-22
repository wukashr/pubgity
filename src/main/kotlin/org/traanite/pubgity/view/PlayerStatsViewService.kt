package org.traanite.pubgity.view

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import org.traanite.pubgity.match.Match
import org.traanite.pubgity.match.MatchService
import org.traanite.pubgity.player.*
import org.traanite.pubgity.view.aggregation.*
import java.time.Instant


@Service
class PlayerStatsViewService(
    private val matchService: MatchService,
    private val playerService: PlayerService,
    private val matchStatsAggregationService: MatchStatsAggregationService,
    private val playerStatsAggregationService: PlayerStatsAggregationService,
    private val matchLabelFormatter: MatchLabelFormatter,
    private val statsSnapshotService: StatsSnapshotService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(PlayerStatsViewService::class.java)
    }

    fun detailByAccountId(accountId: String): PlayerDetailStatsView {
        val player = findPlayer(accountId)
        return detail(player) ?: throw ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR, "Player has no account ID"
        )
    }

    fun detail(player: Player): PlayerDetailStatsView? {
        val accountId = player.accountId ?: run {
            logger.warn("Player '{}' has no accountId, cannot load stats", player.playerName)
            return null
        }

        val playerMatches = loadMatches(player).map {
            PlayerMatchView(
                matchId = it.matchId,
                createdAt = it.createdAt,
                gameMode = matchLabelFormatter.gameMode(it.gameMode),
                mapName = matchLabelFormatter.mapName(it.mapName),
                duration = it.duration,
                botCount = it.botCount,
                playerCount = it.rosters.flatMap { roster -> roster.participants }.size,
                placeTaken = it.rosters.flatMap { roster -> roster.participants }
                    .firstOrNull { p -> p.accountId == accountId }?.matchStats?.winPlace ?: 0
            )
        }

        val latestSnapshot = statsSnapshotService.latestLifetime(accountId)
        val latestSeason = statsSnapshotService.latestSeason(accountId)?.stats
        return PlayerDetailStatsView(
            player = player,
            matches = playerMatches,
            latestStats = latestSnapshot,
            playerAggregated = latestSnapshot?.stats?.let { playerStatsAggregationService.computePlayerAggregatedView(it) },
            lifetimeGameModeStatsView = latestSnapshot?.stats?.gameModeStats
                ?.let { playerStatsAggregationService.computeGameModeStatsView(it) },
            latestSeasonStats = latestSeason,
            playerSeasonAggregated = latestSeason?.let { playerStatsAggregationService.computePlayerSeasonAggregatedView(it) },
            seasonGameModeStatsView = latestSeason?.gameModeStats
                ?.let { playerStatsAggregationService.computeGameModeStatsView(it) }
        )
    }

    fun lifetimeChart(accountId: String): PlayerLifetimeChartView {
        val player = findPlayer(accountId)
        val matches = loadMatches(player)
        val snapshots = statsSnapshotService.loadForMatches(matches, includeSeason = false)
        return PlayerLifetimeChartView(
            player, matchStatsAggregationService.computePerMatchSkillData(matches, accountId, snapshots.lifetime)
        )
    }

    fun seasonChart(accountId: String): PlayerSeasonChartView {
        val player = findPlayer(accountId)
        val matches = loadMatches(player)
        val snapshots = statsSnapshotService.loadForMatches(matches, includeLifetime = false)
        return PlayerSeasonChartView(
            player, matchStatsAggregationService.computePerMatchSeasonSkillData(matches, accountId, snapshots.season)
        )
    }

    private fun findPlayer(accountId: String): Player =
        playerService.findByAccountId(accountId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found")

    private fun loadMatches(player: Player): List<Match> {
        val matches = matchService.findByMatchIds(player.matches.matchIds.toList()).sortedByDescending { it.createdAt }
        logger.info("Player '{}': loaded {} matches", player.playerName, matches.size)
        return matches
    }
}

data class PlayerDetailStatsView(
    val player: Player,
    val matches: List<PlayerMatchView>,
    val latestStats: LifetimeStatsSnapshot?,
    val playerAggregated: PlayerAggregatedView?,
    val lifetimeGameModeStatsView: GameModeStatsView?,
    val latestSeasonStats: SeasonStats?,
    val playerSeasonAggregated: PlayerAggregatedView?,
    val seasonGameModeStatsView: GameModeStatsView?
)

data class PlayerLifetimeChartView(val player: Player, val perMatchStats: List<PerMatchSkillData>)

data class PlayerSeasonChartView(val player: Player, val perMatchSeasonStats: List<PerMatchSeasonSkillData>)

data class PlayerMatchView(
    val matchId: String,
    val createdAt: Instant,
    val gameMode: String,
    val mapName: String,
    val duration: Int,
    val botCount: Int = 0,
    val playerCount: Int,
    val placeTaken: Int
)