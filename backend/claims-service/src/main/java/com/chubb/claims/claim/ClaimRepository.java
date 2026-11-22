package com.chubb.claims.claim;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** List queries load policy, claimant and officer in one go, so a list is one query rather than one per row. */
public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    /** A claimant's own claims, newest first. */
    @EntityGraph(attributePaths = {"policy", "claimant", "assignedOfficer"})
    List<Claim> findByClaimantIdOrderBySubmittedAtDesc(UUID claimantId);

    /** Staff queue, "All": every claim, newest first. */
    @EntityGraph(attributePaths = {"policy", "claimant", "assignedOfficer"})
    List<Claim> findAllByOrderBySubmittedAtDesc();

    /** Staff queue, "Unassigned": claims waiting to be picked up. */
    @EntityGraph(attributePaths = {"policy", "claimant", "assignedOfficer"})
    List<Claim> findByAssignedOfficerIsNullOrderBySubmittedAtDesc();

    /** Staff queue, "Assigned to me": every claim the officer holds, in any status. */
    @EntityGraph(attributePaths = {"policy", "claimant", "assignedOfficer"})
    List<Claim> findByAssignedOfficerIdOrderBySubmittedAtDesc(UUID officerId);

    /**
     * One claim with what the detail view shows. History is fetched here; information requests load lazily
     * (Hibernate can't fetch two lists in one query).
     */
    @EntityGraph(attributePaths = {"policy", "claimant", "assignedOfficer", "history", "history.changedBy"})
    Optional<Claim> findDetailedById(UUID id);

    /** Next value for the human-readable claim number (CLM-000042). */
    @Query(value = "SELECT nextval('claim_number_seq')", nativeQuery = true)
    long nextClaimNumber();
}
