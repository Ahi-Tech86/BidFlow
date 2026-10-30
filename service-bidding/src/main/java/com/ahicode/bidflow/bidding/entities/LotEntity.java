package com.ahicode.bidflow.bidding.entities;

import com.ahicode.bidflow.bidding.enums.LotStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Setter
@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "lot")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class LotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(length = 50, nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, updatable = false)
    private BigDecimal startPrice;

    @Builder.Default
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private LotStatus status = LotStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private BigDecimal minBidStep;

    @Column(nullable = false, updatable = false)
    private ZonedDateTime finishedAt;

    @Column(nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        ZonedDateTime now = ZonedDateTime.now();

        if (this.createdAt == null) {
            this.createdAt = now;
        }

        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }
}
