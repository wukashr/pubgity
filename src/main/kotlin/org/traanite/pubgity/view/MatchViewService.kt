package org.traanite.pubgity.view

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import org.traanite.pubgity.match.MatchService
import org.traanite.pubgity.player.Player
import org.traanite.pubgity.player.PlayerService
import org.traanite.pubgity.player.StatsSnapshotService
import org.traanite.pubgity.pubgapi.toParticipantLifetimeStats
import org.traanite.pubgity.view.aggregation.MatchStatsAggregationService

@Service
class MatchViewService(
    private val matches: MatchService,
    private val players: PlayerService,
    private val aggregation: MatchStatsAggregationService,
    private val mapper: MatchViewMapper,
    private val snapshots: StatsSnapshotService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(MatchViewService::class.java)
    }

    fun detail(accountId: String, matchId: String): MatchPageView {
        logger.info("Loading match detail: accountId={}, matchId={}", accountId, matchId)
        val player = players.findByAccountId(accountId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found")
        val match = matches.findByMatchId(matchId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found")
        val lifetime = snapshots.loadForMatches(listOf(match), includeSeason = false).lifetime
        val participants = match.rosters.flatMap { roster ->
            roster.participants.map { participant ->
                val stats = participant.lifetimeStatsSnapshotId?.let {
                    aggregation.aggregateParticipantStats(
                        lifetime.getValue(it).stats.toParticipantLifetimeStats(), match.gameMode
                    )
                }
                participant.toParticipantView(roster.rank, stats)
            }
        }.sortedByDescending { it.kills }
        return MatchPageView(player, mapper.toDetail(match), participants)
    }
}

data class MatchPageView(
    val player: Player,
    val match: MatchDetailView,
    val participants: List<ParticipantView>
)
