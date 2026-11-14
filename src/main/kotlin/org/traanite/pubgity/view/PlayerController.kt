package org.traanite.pubgity.view

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.traanite.pubgity.player.PlayerService

@Controller
@RequestMapping("/players")
class PlayerController(
    private val playerService: PlayerService,
    private val playerStatsViewService: PlayerStatsViewService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(PlayerController::class.java)
    }

    // todo dmg / round (mode)
    //  dmg / round (overall)
    //  move kd by mode calculation to aggregator
    @GetMapping
    fun listPlayers(@RequestParam(required = false) filter: String?, model: Model): String {
        val players = if (filter.isNullOrBlank()) {
            playerService.findAll()
        } else {
            playerService.searchByPlayerName(filter)
        }
        logger.debug("Player list: filter='{}', found {} players", filter ?: "", players.size)
        model.addAttribute("players", players.sortedByDescending { it.matches.matchIds.size })
        model.addAttribute("filter", filter ?: "")
        return "players"
    }

    // todo performance of this call with 30 matches takes around a second
    //  could lead to performance issues with more users
    //  caching on aggregation service desirable

    // todo replace with ObjectId
    @GetMapping("/{accountId}")
    fun playerDetail(@PathVariable accountId: String, model: Model): String {
        if (accountId.isBlank()) {
            return "players"
        }
        playerStatsViewService.populateModelByAccountId(accountId, model)
        return "player-detail"
    }

    @GetMapping("/{accountId}/charts/lifetime")
    fun playerLifetimeCharts(@PathVariable accountId: String, model: Model): String {
        playerStatsViewService.populateModelByAccountId(accountId, model)
        return "player-lifetime-charts"
    }

    @GetMapping("/{accountId}/charts/season")
    fun playerSeasonCharts(@PathVariable accountId: String, model: Model): String {
        playerStatsViewService.populateModelByAccountId(accountId, model)
        return "player-season-charts"
    }

    @PostMapping("/{accountId}/privacy-protected/{isPrivacyProtected}")
    fun setPrivacyProtected(
        @PathVariable accountId: String,
        @PathVariable isPrivacyProtected: Boolean
    ): String {
        playerService.setPrivacyProtected(accountId, isPrivacyProtected)
        logger.info("Admin set isPrivacyProtected={} for accountId={}", isPrivacyProtected, accountId)
        return "redirect:/players/$accountId"
    }
}
