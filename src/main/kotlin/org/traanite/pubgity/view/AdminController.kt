package org.traanite.pubgity.view

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*

@Controller
@RequestMapping("/admin")
class AdminController(private val views: AdminViewService) {
    @GetMapping("/users")
    fun listUsers(model: Model): String {
        val view = views.listUsers()
        model.addAttribute("users", view.users)
        model.addAttribute("players", view.players)
        return "admin/users"
    }


    @PostMapping("/users/{id}/moderator-constraints")
    fun updateModeratorConstraints(
        @PathVariable id: String,
        @RequestParam(defaultValue = "5") maxQueueSize: Int,
        @RequestParam(required = false) allowedPlayerIds: List<String>?
    ): String {
        views.updateModeratorConstraints(id, allowedPlayerIds, maxQueueSize)
        return "redirect:/admin/users"
    }
}
