package com.smu.healyx.cost.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "region_adjustment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RegionAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "region_adj_id")
    private Long regionAdjId;

    @Column(name = "region", nullable = false, length = 20)
    private String region;

    @Column(name = "department", nullable = false, length = 30)
    private String department;

    @Column(name = "adj_factor", nullable = false)
    private double adjFactor;

    @Column(name = "adj_factor_full", nullable = false)
    private double adjFactorFull;
}
