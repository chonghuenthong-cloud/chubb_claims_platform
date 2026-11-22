package com.chubb.claims.policy;

import com.chubb.claims.user.AppUser;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** An insurance policy held by a claimant. Claims are always made against a policy. */
@Entity
@Table(name = "policy")
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "policy_number", nullable = false, unique = true)
    private String policyNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "holder_id")
    private AppUser holder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Market market;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false)
    private ProductType productType;

    /** Maximum the insurer pays on one claim; caps the reserve. */
    @Column(name = "coverage_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal coverageLimit;

    /** ISO 4217 code, e.g. SGD. Amounts on this policy's claims are in this currency. */
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Required by JPA. */
    protected Policy() {
    }

    public Policy(String policyNumber, AppUser holder, Market market, ProductType productType,
                  BigDecimal coverageLimit, String currency, LocalDate startDate, LocalDate endDate) {
        this.policyNumber = policyNumber;
        this.holder = holder;
        this.market = market;
        this.productType = productType;
        this.coverageLimit = coverageLimit;
        this.currency = currency;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /** True if the date falls within the policy period (start inclusive, end exclusive). */
    public boolean covers(LocalDate date) {
        return !date.isBefore(startDate) && date.isBefore(endDate);
    }

    public UUID getId() { return id; }
    public String getPolicyNumber() { return policyNumber; }
    public AppUser getHolder() { return holder; }
    public Market getMarket() { return market; }
    public ProductType getProductType() { return productType; }
    public BigDecimal getCoverageLimit() { return coverageLimit; }
    public String getCurrency() { return currency; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
}
