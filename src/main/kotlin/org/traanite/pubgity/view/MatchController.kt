package org.traanite.pubgity.view

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/players/{accountId}/matches")
class MatchController(private val views: MatchViewService) {
    @GetMapping("/{matchId}")
    fun matchDetail(@PathVariable accountId: String, @PathVariable matchId: String, model: Model): String {
        val detail = views.detail(accountId, matchId)
        model.addAttribute("player", detail.player)
        model.addAttribute("match", detail.match)
        model.addAttribute("participants", detail.participants)
        return "match-detail"
    }
}
