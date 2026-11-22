package com.chubb.claims.claim;

import com.chubb.claims.user.AppUser;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Audit trail entry: who moved a claim from which status to which, and when. Never updated. */
@Entity
@Table(name = "claim_status_history")
public class ClaimStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id")
    private Claim claim;

    /** Null for the initial submission. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private ClaimStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private ClaimStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by")
    private AppUser changedBy;

    private String note;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    /** Required by JPA. */
    protected ClaimStatusHistory() {
    }

    ClaimStatusHistory(Claim claim, ClaimStatus fromStatus, ClaimStatus toStatus, AppUser changedBy, String note) {
        this.claim = claim;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.note = note;
    }

    public UUID getId() { return id; }
    public Claim getClaim() { return claim; }
    public ClaimStatus getFromStatus() { return fromStatus; }
    public ClaimStatus getToStatus() { return toStatus; }
    public AppUser getChangedBy() { return changedBy; }
    public String getNote() { return note; }
    public Instant getChangedAt() { return changedAt; }
}
