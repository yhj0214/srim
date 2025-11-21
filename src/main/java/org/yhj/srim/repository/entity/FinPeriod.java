package org.yhj.srim.repository.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fin_period",
        uniqueConstraints = {
                @UniqueConstraint(name = "UN_FIN_PERIOD",
                        columnNames = {"company_id", "period_type", "fiscal_year", "fiscal_quarter", "is_estimate"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Comment("재무 기간 정의(연/분기/추정 포함)")
public class FinPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "period_id")
    @Comment("PK: 기간 ID")
    private Long periodId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @Comment("FK → company.company_id")
    private Company company;

    @Column(name = "period_type", nullable = false, length = 8)
    @Comment("기간 유형: YEAR(연간) / QTR(분기)")
    private String periodType;

    @Column(name = "fiscal_year", nullable = false)
    @Comment("회계연도(예: 2024)")
    private Integer fiscalYear;

    @Column(name = "fiscal_quarter")
    @Comment("분기(1~4, 연간이면 NULL)")
    private Integer fiscalQuarter;

    @Column(name = "is_estimate", nullable = false)
    @Comment("추정치 여부(E)")
    @Builder.Default
    private Boolean isEstimate = false;

    @Column(name = "label", nullable = false, length = 20)
    @Comment("표시용 라벨(YYYY/12, YYYY.Q#)")
    private String label;

    @Column(name = "period_start")
    @Comment("기간 시작일(선택)")
    private LocalDate periodStart;

    @Column(name = "period_end")
    @Comment("기간 종료일(선택)")
    private LocalDate periodEnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Comment("행 생성시각")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Comment("행 수정시각")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
