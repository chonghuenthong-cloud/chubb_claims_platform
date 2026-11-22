package com.chubb.claims.dashboard;

import com.chubb.claims.user.CurrentUserService;
import com.chubb.claims.user.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Team workload, liability exposure and performance, for staff. */
@RestController
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentUserService currentUser;

    public DashboardController(DashboardService dashboardService, CurrentUserService currentUser) {
        this.dashboardService = dashboardService;
        this.currentUser = currentUser;
    }

    /** All four dashboard sections in one response. Staff only. */
    @GetMapping("/api/dashboard")
    public DashboardResponse dashboard(
            @RequestHeader(value = CurrentUserService.USER_HEADER, required = false) UUID userId) {
        currentUser.require(userId, Role.STAFF);
        return dashboardService.dashboard();
    }
}
