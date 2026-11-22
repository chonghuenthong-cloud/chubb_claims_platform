package com.chubb.claims.claim;

import com.chubb.claims.user.AppUser;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** A question from staff to the claimant. The claim waits in AWAITING_INFO until it is answered. */
@Entity
@Table(name = "information_request")
public class InformationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id")
    private Claim claim;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by")
    private AppUser requestedBy;

    @Column(nullable = false)
    private String question;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    private String response;

    @Column(name = "responded_at")
    private Instant respondedAt;

    /** Required by JPA. */
    protected InformationRequest() {
    }

    public InformationRequest(Claim claim, AppUser requestedBy, String question) {
        this.claim = claim;
        this.requestedBy = requestedBy;
        this.question = question;
    }

    /** Records the claimant's answer. */
    public void respond(String response) {
        this.response = response;
        this.respondedAt = Instant.now();
    }

    public boolean isAnswered() {
        return respondedAt != null;
    }

    public UUID getId() { return id; }
    public Claim getClaim() { return claim; }
    public AppUser getRequestedBy() { return requestedBy; }
    public String getQuestion() { return question; }
    public Instant getRequestedAt() { return requestedAt; }
    public String getResponse() { return response; }
    public Instant getRespondedAt() { return respondedAt; }
}
