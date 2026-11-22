package com.chubb.claims.policy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {

    /** A claimant may only claim against their own policies. */
    List<Policy> findByHolderId(UUID holderId);
}
