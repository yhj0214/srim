package org.yhj.srim.repository.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fin_metric_value")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Comment("재무 지표 값(연/분기/추정 전체 커버)")
public class FinMetricValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "value_id")
    @Comment("PK: 내부 식별자")
    private Long id;

    @Column(name = "company_id", nullable = false)
    @Comment("FK → company.company_id")
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id", nullable = false)
    @Comment("FK → fin_period.period_id")
    private FinPeriod period;

    @Column(name = "metric_code", length = 32, nullable = false)
    @Comment("FK → fin_metric_def.metric_code")
    private String metricCode;

    @Column(name = "value_num", precision = 28, scale = 6)
    @Comment("지표 수치(소수/원 단위 통일 저장)")
    private BigDecimal valueNum;

    @Column(name = "source", length = 20)
    @Comment("수집원(KRX/NAVER/FNG/CSV/MANUAL)")
    private String source;

    @Column(name = "updated_at", nullable = false)
    @Comment("갱신 시각")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
