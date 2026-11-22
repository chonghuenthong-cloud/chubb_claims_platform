package com.chubb.claims.policy;

import com.chubb.claims.user.AppUser;
import com.chubb.claims.user.CurrentUserService;
import com.chubb.claims.user.Role;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Policies seen from the claimant's side: what they can claim against. */
@RestController
@RequestMapping("/api/me/policies")
public class PolicyController {

    /** A policy as shown in the "Report an incident" dropdown. */
    public record PolicyResponse(UUID id, String policyNumber, Market market, ProductType productType,
                                 BigDecimal coverageLimit, String currency, LocalDate startDate, LocalDate endDate) {

        static PolicyResponse from(Policy p) {
            return new PolicyResponse(p.getId(), p.getPolicyNumber(), p.getMarket(), p.getProductType(),
                    p.getCoverageLimit(), p.getCurrency(), p.getStartDate(), p.getEndDate());
        }
    }

    private final PolicyRepository policies;
    private final CurrentUserService currentUser;

    public PolicyController(PolicyRepository policies, CurrentUserService currentUser) {
        this.policies = policies;
        this.currentUser = currentUser;
    }

    /** The calling claimant's own policies. */
    @GetMapping
    @Transactional(readOnly = true)
    public List<PolicyResponse> myPolicies(
            @RequestHeader(value = CurrentUserService.USER_HEADER, required = false) UUID userId) {
        AppUser claimant = currentUser.require(userId, Role.CLAIMANT);
        return policies.findByHolderIdOrderByPolicyNumber(claimant.getId()).stream()
                .map(PolicyResponse::from)
                .toList();
    }
}
