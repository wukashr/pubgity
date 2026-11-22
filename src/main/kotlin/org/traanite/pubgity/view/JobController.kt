package org.traanite.pubgity.view

import org.bson.types.ObjectId
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/jobs")
class JobController(private val views: JobsViewService) {
    @GetMapping
    fun jobs(model: Model, auth: Authentication): String {
        val view = views.list(auth)
        model.addAttribute("players", view.players)
        model.addAttribute("fetchMatchStatsJobs", view.fetchMatchStatsJobs)
        model.addAttribute("fetchPlayerMatchesJobs", view.fetchPlayerMatchesJobs)
        model.addAttribute("canManagePlayers", view.canManagePlayers)
        model.addAttribute("moderatorMatchCountCap", view.moderatorMatchCountCap)
        return "jobs"
    }

    @PostMapping("/{jobId}/retry")
    fun retryJob(@PathVariable jobId: ObjectId, auth: Authentication, redirectAttributes: RedirectAttributes): String =
        redirect(views.retry(jobId, auth), redirectAttributes)

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/players/add")
    fun addPlayer(@RequestParam playerName: String): String {
        views.addPlayer(playerName)
        return "redirect:/jobs"
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/players/{id}/remove")
    fun removePlayer(@PathVariable id: String): String {
        views.removePlayer(id)
        return "redirect:/jobs"
    }

    @PostMapping("/players/{id}/update")
    fun updatePlayer(
        @PathVariable id: ObjectId,
        @RequestParam(defaultValue = "5") matchCount: Int,
        auth: Authentication,
        redirectAttributes: RedirectAttributes
    ): String = redirect(views.updatePlayer(id, matchCount, auth), redirectAttributes)

    @PostMapping("/{id}/cancel")
    fun cancelJob(@PathVariable id: String, auth: Authentication, redirectAttributes: RedirectAttributes): String =
        redirect(views.cancel(id, auth), redirectAttributes)

    private fun redirect(result: JobActionResult, attributes: RedirectAttributes): String {
        result.error?.let { attributes.addFlashAttribute("error", it) }
        return "redirect:/jobs"
    }
}
