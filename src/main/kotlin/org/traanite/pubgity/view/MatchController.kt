package org.traanite.pubgity.view

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.server.ResponseStatusException
import org.traanite.pubgity.match.MatchService
import org.traanite.pubgity.player.PlayerService
import org.traanite.pubgity.view.aggregation.MatchStatsAggregationService

@Controller
@RequestMapping("/players/{accountId}/matches")
class MatchController(
    private val matchService: MatchService,
    private val playerService: PlayerService,
    private val matchStatsAggregationService: MatchStatsAggregationService,
    private val matchViewMapper: MatchViewMapper
) {
    companion object {
        private val logger = LoggerFactory.getLogger(MatchController::class.java)
    }

    // todo a new chart
    //  for all the participants
    //  measure averages etc
    //  for now-x seasons stats if available

    // todo dmg / round (mode)
    //  dmg / round (overall)
    //  player stats of the match
    @GetMapping("/{matchId}")
    fun matchDetail(
        @PathVariable accountId: String,
        @PathVariable matchId: String,
        model: Model
    ): String {
        logger.info("Loading match detail: accountId={}, matchId={}", accountId, matchId)
        val player = playerService.findByAccountId(accountId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found")
        val match = matchService.findByMatchId(matchId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found")

        val participantsWithMode = match.rosters.flatMap { roster ->
                roster.participants.map { p -> Pair(roster, p) }
            }
            .map { (roster, p) ->
                val agg = p.lifetimeStatsSnapshot?.let {
                    matchStatsAggregationService.aggregateParticipantStats(it, match.gameMode)
                }
                p.toParticipantView(roster.rank, agg)
            }
            .sortedByDescending { it.kills }

        model.addAttribute("player", player)
        model.addAttribute("match", matchViewMapper.toDetail(match))
        model.addAttribute("participants", participantsWithMode)
        return "match-detail"
    }
}
