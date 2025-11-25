package org.yhj.srim.repository.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Entity
@Table(name = "stock_share_status",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UNQ_SSS_COMPANY_YEAR_SE",
                        columnNames = {"company_id", "bsns_year", "se"}
                )
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class StockShareStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_status_id")
    @Comment("PK")
    private Long stockStatusId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_SSS_COMPANY"))
    @Comment("FK: company.company_id")
    private Company company;

    @Column(name = "bsns_year", nullable = false)
    @Comment("사업연도(예: 2024)")
    private Integer bsnsYear;

    @Column(name = "stlm_dt", nullable = false)
    @Comment("결산일(예: 2018-12-31)")
    private LocalDate settlementDate;

    @Column(name = "se", length = 20, nullable = false)
    @Comment("주식종류(보통주/우선주/합계 등)")
    private String se;

    @Column(name = "isu_stock_totqy")
    @Comment("발행할 주식의 총수(정관상 한도)")
    private Long isuStockTotqy;

    @Column(name = "istc_totqy")
    @Comment("발행주식의 총수(istc_totqy)")
    private Long istcTotqy;

    @Column(name = "tesstk_co")
    @Comment("자기주식수")
    private Long tesstkCo;

    @Column(name = "distb_stock_co")
    @Comment("유통주식수")
    private Long distbStockCo;

    @Column(name = "created_at", nullable = false,
            updatable = false, insertable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false,
            insertable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public static StockShareStatus create(
            Company company,
            int bsnsYear,
            LocalDate stlmDt,
            String se,
            Long isuStockTotqy,
            Long istcTotqy,
            Long tesstkCo,
            Long distbStockCo
    ) {
        StockShareStatus s = new StockShareStatus();
        s.company = company;
        s.bsnsYear = bsnsYear;
        s.settlementDate = stlmDt;
        s.se = se;
        s.isuStockTotqy = isuStockTotqy;
        s.istcTotqy = istcTotqy;
        s.tesstkCo = tesstkCo;
        s.distbStockCo = distbStockCo;
        return s;
    }
}