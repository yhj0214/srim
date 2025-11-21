package org.yhj.srim.repository.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bond_yield_curve",
        uniqueConstraints = {
                @UniqueConstraint(name = "UN_BOND_CURVE_UNIQ",
                        columnNames = {"as_of", "rating", "tenor_months"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Comment("회사채/국고채 수익률 곡선(등급×만기×일자)")
public class BondYieldCurve {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "curve_id")
    @Comment("PK: 곡선 ID")
    private Long curveId;

    @Column(name = "as_of", nullable = false)
    @Comment("기준일(YYYY-MM-DD)")
    private LocalDate asOf;

    @Column(name = "rating", nullable = false, length = 8)
    @Comment("신용등급(AAA/AA+/AA/A/BBB..., 국고채는 KTB 등으로 표기)")
    private String rating;

    @Column(name = "tenor_months", nullable = false)
    @Comment("만기(월 단위: 3,6,9,12,18,24,36,60 등)")
    private Short tenorMonths;

    @Column(name = "yield_rate", nullable = false, precision = 10, scale = 4)
    @Comment("수익률(소수, 0.0286 = 2.86%)")
    private BigDecimal yieldRate;

    @Column(name = "source", length = 20)
    @Comment("수집원(KOFIA/KRX/ETC)")
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Comment("행 생성시각")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
