package org.yhj.srim.repository.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

@Entity
@Table(name = "fin_metric_def",
        uniqueConstraints = {
                @UniqueConstraint(name = "UN_FMD_NAME", columnNames = {"name_kor"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Comment("재무 지표 마스터(카탈로그: 코드/단위/순서)")
public class FinMetricDef {

    @Id
    @Column(name = "metric_code", length = 32)
    @Comment("지표 코드(SALES/OP_INC/NET_INC/ROE/EPS/BPS/PER/PBR 등)")
    private String metricCode;

    @Column(name = "name_kor", nullable = false, length = 100)
    @Comment("지표 명칭(국문)")
    private String nameKor;

    @Column(name = "unit", length = 20)
    @Comment("표시 단위(KRW/%/배/주 등)")
    private String unit;

    @Column(name = "description", length = 500)
    @Comment("지표 설명(산식/출처 등)")
    private String description;

    @Column(name = "display_order", nullable = false)
    @Comment("화면 표기 순서(보고서/대시보드 정렬 기준)")
    private Integer displayOrder;
}
