package org.yhj.srim.repository.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "stock_price")
public class StockPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "price_id", nullable = false)
    @Comment("PK: 주식가격 ID")
    private Long priceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @Comment("FK → company.company_id")
    private Company company;

    @Column(name = "as_of", nullable = false)
    @Comment("수집 시각(현지시간)")
    private LocalDateTime asOf;

    @Column(name = "price", precision = 18, scale = 2)
    @Comment("현재가/종가")
    private BigDecimal price;

    @Column(name = "open_price", precision = 18, scale = 2)
    @Comment("시가")
    private BigDecimal openPrice;

    @Column(name = "high_price", precision = 18, scale = 2)
    @Comment("고가")
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 18, scale = 2)
    @Comment("저가")
    private BigDecimal lowPrice;

    @Column(name = "volume")
    @Comment("거래량")
    private Long volume;

    @Column(name = "market_cap", precision = 22, scale = 2)
    @Comment("시가총액")
    private BigDecimal marketCap;

    @Column(name = "per", precision = 10, scale = 4)
    @Comment("PER")
    private BigDecimal per;

    @Column(name = "pbr", precision = 10, scale = 4)
    @Comment("PBR")
    private BigDecimal pbr;

    @Column(name = "div_yield", precision = 10, scale = 4)
    @Comment("현금배당수익률(소수, 0.045 = 4.5%)")
    private BigDecimal divYield;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20, nullable = false)
    @Comment("수집원(NAVER/KRX/FNG/CSV/MANUAL)")
    private MarketSnapshotSource source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate(){
        if(this.createdAt == null){
            this.createdAt = LocalDateTime.now();
        }
    }

    public enum MarketSnapshotSource {
        NAVER,
        KRX,
        FNG,
        CSV,
        MANUAL
    }
}
