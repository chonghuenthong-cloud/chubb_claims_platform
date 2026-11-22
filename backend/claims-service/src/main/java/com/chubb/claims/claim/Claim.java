package com.chubb.claims.claim;

import com.chubb.claims.policy.Policy;
import com.chubb.claims.user.AppUser;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The central entity: a claimant's request for payment under a policy.
 * All status changes go through {@link #changeStatus}, which enforces the lifecycle and records history.
 */
@Entity
@Table(name = "claim")
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Human-readable reference shown to claimants, e.g. CLM-000042. */
    @Column(name = "claim_number", nullable = false, unique = true)
    private String claimNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id")
    private Policy policy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claimant_id")
    private AppUser claimant;

    /** Staff member handling the claim; null while it waits in the queue. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_officer_id")
    private AppUser assignedOfficer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(name = "incident_description", nullable = false)
    private String incidentDescription;

    /** What the claimant thinks the loss is. */
    @Column(name = "estimated_loss", nullable = false, precision = 15, scale = 2)
    private BigDecimal estimatedLoss;

    /** What the insurer expects to pay. Drives the liability exposure figure. */
    @Column(name = "reserve_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal reserveAmount;

    @Column(name = "approved_amount", precision = 15, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic lock: stops two officers from silently overwriting each other's changes. */
    @Version
    private long version;

    /** Saved together with the claim; read in time order. */
    @OneToMany(mappedBy = "claim", cascade = CascadeType.PERSIST)
    @OrderBy("changedAt ASC")
    private List<ClaimStatusHistory> history = new ArrayList<>();

    /** Questions from the officer and the claimant's answers; saved with the claim, read in time order. */
    @OneToMany(mappedBy = "claim", cascade = CascadeType.PERSIST)
    @OrderBy("requestedAt ASC")
    private List<InformationRequest> informationRequests = new ArrayList<>();

    /** Events recorded by the actions below; ClaimService saves them to the outbox in the same transaction. */
    @Transient
    private final List<PendingClaimEvent> pendingEvents = new ArrayList<>();

    /** Required by JPA. */
    protected Claim() {
    }

    /**
     * Creates a new claim in SUBMITTED status.
     * The initial reserve is the claimant's estimate, capped at the policy's coverage limit
     * (an assumption; an officer can revise it during review).
     */
    public static Claim submit(String claimNumber, Policy policy, AppUser claimant,
                               LocalDate incidentDate, String incidentDescription, BigDecimal estimatedLoss) {
        Claim claim = new Claim();
        claim.claimNumber = claimNumber;
        claim.policy = policy;
        claim.claimant = claimant;
        claim.incidentDate = incidentDate;
        claim.incidentDescription = incidentDescription;
        claim.estimatedLoss = estimatedLoss;
        claim.reserveAmount = estimatedLoss.min(policy.getCoverageLimit());
        claim.status = ClaimStatus.SUBMITTED;
        claim.history.add(new ClaimStatusHistory(claim, null, ClaimStatus.SUBMITTED, claimant, null));
        claim.record(ClaimEventType.CLAIM_SUBMITTED, claimant, null, null, BigDecimal.ZERO);
        return claim;
    }

    /**
     * Moves the claim to {@code target} and records who did it in the history.
     * Package-private: outside code goes through the actions below, which also record an event.
     *
     * @throws InvalidStatusTransitionException if the lifecycle does not allow the move
     */
    void changeStatus(ClaimStatus target, AppUser changedBy, String note) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(status, target);
        }
        history.add(new ClaimStatusHistory(this, status, target, changedBy, note));
        status = target;
    }

    /**
     * A status change plus its side effects (assigning, saving amounts...), recorded as one
     * CLAIM_STATUS_CHANGED event once the effects are applied, so the event carries the final reserve.
     */
    private void transition(ClaimStatus target, AppUser actor, String note, Runnable effects) {
        ClaimStatus from = status;
        BigDecimal reserveBefore = reserveAmount;
        changeStatus(target, actor, note);
        effects.run();
        record(ClaimEventType.CLAIM_STATUS_CHANGED, actor, from, note, reserveBefore);
    }

    /** Records an event describing the claim as it is now. */
    private void record(ClaimEventType type, AppUser actor, ClaimStatus from, String note, BigDecimal reserveBefore) {
        pendingEvents.add(new PendingClaimEvent(UUID.randomUUID(), type, Instant.now(), actor,
                from, status, note, reserveBefore, reserveAmount));
    }

    /** Hands over the recorded events and clears them, so each is saved to the outbox exactly once. */
    public List<PendingClaimEvent> pullPendingEvents() {
        List<PendingClaimEvent> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }

    // ---------------------------------------------------------------------------------------------
    // Staff and claimant actions. Each checks, in order: ownership (403), status (409),
    // amount within coverage limit (400), then acts.
    // ---------------------------------------------------------------------------------------------

    /** An officer takes an unassigned claim: it becomes theirs and moves to UNDER_REVIEW. */
    public void pickUp(AppUser officer) {
        if (assignedOfficer != null) {
            throw new InvalidStatusTransitionException(
                    "Claim has already been picked up by " + assignedOfficer.getFullName());
        }
        transition(ClaimStatus.UNDER_REVIEW, officer, null, () -> assignedOfficer = officer);
    }

    /** The assigned officer updates the expected liability while assessing (before a decision). */
    public void reviseReserve(AppUser officer, BigDecimal amount) {
        requireAssignedOfficer(officer);
        if (status != ClaimStatus.UNDER_REVIEW && status != ClaimStatus.AWAITING_INFO) {
            throw new InvalidStatusTransitionException("The reserve can only be revised while the claim is being assessed");
        }
        requireWithinCoverageLimit(amount);
        BigDecimal before = reserveAmount;
        reserveAmount = amount;
        record(ClaimEventType.RESERVE_REVISED, officer, status, null, before);
    }

    /** The assigned officer asks the claimant a question; the claim waits in AWAITING_INFO. */
    public void requestInformation(AppUser officer, String question) {
        requireAssignedOfficer(officer);
        transition(ClaimStatus.AWAITING_INFO, officer, question,
                () -> informationRequests.add(new InformationRequest(this, officer, question)));
    }

    /** The claimant answers the open question; the claim goes back to UNDER_REVIEW. */
    public void answerInformationRequest(AppUser claimant, String response) {
        InformationRequest open = informationRequests.stream()
                .filter(r -> !r.isAnswered())
                .reduce((first, second) -> second)   // latest open question
                .orElse(null);
        if (status != ClaimStatus.AWAITING_INFO || open == null) {
            throw new InvalidStatusTransitionException("There is no open question to answer on this claim");
        }
        transition(ClaimStatus.UNDER_REVIEW, claimant, null, () -> open.respond(response));
    }

    /** The assigned officer approves an amount; the reserve becomes that amount (what we still expect to pay). */
    public void approve(AppUser officer, BigDecimal amount) {
        requireAssignedOfficer(officer);
        if (!status.canTransitionTo(ClaimStatus.APPROVED)) {
            throw new InvalidStatusTransitionException(status, ClaimStatus.APPROVED);
        }
        requireWithinCoverageLimit(amount);
        transition(ClaimStatus.APPROVED, officer, null, () -> {
            approvedAmount = amount;
            reserveAmount = amount;
        });
    }

    /** The assigned officer rejects the claim with a reason; nothing is left to pay, so the reserve is 0. */
    public void reject(AppUser officer, String reason) {
        requireAssignedOfficer(officer);
        transition(ClaimStatus.REJECTED, officer, reason, () -> {
            rejectionReason = reason;
            reserveAmount = BigDecimal.ZERO;
        });
    }

    /** The assigned officer confirms the approved amount was paid; nothing is left to pay, so the reserve is 0. */
    public void settle(AppUser officer) {
        requireAssignedOfficer(officer);
        transition(ClaimStatus.SETTLED, officer, null, () -> reserveAmount = BigDecimal.ZERO);
    }

    private void requireWithinCoverageLimit(BigDecimal amount) {
        if (amount.compareTo(policy.getCoverageLimit()) > 0) {
            throw new AmountAboveCoverageLimitException(policy.getCoverageLimit(), policy.getCurrency());
        }
    }

    private void requireAssignedOfficer(AppUser officer) {
        if (!isAssignedTo(officer)) {
            throw new NotAssignedOfficerException();
        }
    }

    /** Compares by id once saved; by object identity before (in unit tests, ids are not set). */
    public boolean isAssignedTo(AppUser user) {
        if (assignedOfficer == null || user == null) return false;
        if (assignedOfficer == user) return true;
        return assignedOfficer.getId() != null && assignedOfficer.getId().equals(user.getId());
    }

    public UUID getId() { return id; }
    public String getClaimNumber() { return claimNumber; }
    public Policy getPolicy() { return policy; }
    public AppUser getClaimant() { return claimant; }
    public AppUser getAssignedOfficer() { return assignedOfficer; }
    public ClaimStatus getStatus() { return status; }
    public LocalDate getIncidentDate() { return incidentDate; }
    public String getIncidentDescription() { return incidentDescription; }
    public BigDecimal getEstimatedLoss() { return estimatedLoss; }
    public BigDecimal getReserveAmount() { return reserveAmount; }
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public List<ClaimStatusHistory> getHistory() { return Collections.unmodifiableList(history); }
    public List<InformationRequest> getInformationRequests() { return Collections.unmodifiableList(informationRequests); }
}
