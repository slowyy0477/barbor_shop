package com.ayan.salon.server.web;

import com.ayan.salon.server.service.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/salons/{salonId}/dashboard")
public class DashboardController {
    private final DashboardService dashboard;
    private final OwnerOverviewService ownerOverview;
    private final AuthenticatedActorResolver actors;
    public DashboardController(DashboardService dashboard, OwnerOverviewService ownerOverview, AuthenticatedActorResolver actors) { this.dashboard = dashboard; this.ownerOverview = ownerOverview; this.actors = actors; }
    @GetMapping("/today")
    public DashboardService.Summary today(@PathVariable UUID salonId) { ActorContext actor = actors.require(); actor.requireSalon(salonId); return dashboard.today(actor); }

    @GetMapping("/overview")
    public OwnerOverviewService.Overview overview(@PathVariable UUID salonId) {
        ActorContext actor = actors.require(); actor.requireSalon(salonId);
        return ownerOverview.get(actor);
    }
}
