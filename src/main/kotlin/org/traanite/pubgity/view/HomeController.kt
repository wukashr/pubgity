package org.traanite.pubgity.view

import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class HomeController(private val views: HomeViewService) {

    @GetMapping("/")
    fun home(auth: Authentication?, model: Model): String {
        val view = views.home(auth)
        if (view.noLinkedPlayer) model.addAttribute("noLinkedPlayer", true)
        view.detail?.let { detail ->
            model.addAttribute("player", detail.player)
            model.addAttribute("matches", detail.matches)
            model.addAttribute("playerAggregated", detail.playerAggregated)
        }
        return "home"
    }
}
