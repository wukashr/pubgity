package org.traanite.pubgity.view

import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
@RequestMapping("/profile")
class ProfileController(private val views: ProfileViewService) {
    @GetMapping
    fun profile(auth: Authentication, model: Model): String {
        val view = views.profile(auth) ?: return "redirect:/"
        model.addAttribute("appUser", view.appUser)
        model.addAttribute("linkedPlayer", view.linkedPlayer)
        model.addAttribute("oidcAccountUrl", view.oidcAccountUrl)
        return "profile"
    }

    @PostMapping("/username")
    fun updateUsername(
        auth: Authentication,
        @RequestParam username: String
    ): String = redirect(views.updateUsername(auth, username))

    @PostMapping("/link-player")
    fun linkPlayer(auth: Authentication, @RequestParam playerName: String): String =
        redirect(views.linkPlayer(auth, playerName))

    @PostMapping("/unlink-player")
    fun unlinkPlayer(auth: Authentication): String = redirect(views.unlinkPlayer(auth))

    private fun redirect(result: ProfileActionResult): String = when (result) {
        ProfileActionResult.SUCCESS -> "redirect:/profile"
        ProfileActionResult.NO_USER -> "redirect:/"
        ProfileActionResult.INVALID_USERNAME -> "redirect:/profile?error=invalidUsername"
        ProfileActionResult.PLAYER_NOT_FOUND -> "redirect:/profile?error=playerNotFound"
    }
}
