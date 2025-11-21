package org.yhj.srim.repository.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_code",
        uniqueConstraints = {
                @UniqueConstraint(name = "UN_STOCK_CODE_MARKET_TICKER", 
                                columnNames = {"market", "ticker_krx"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Comment("KRX 종목코드 마스터(기업 식별 기본정보)")
public class StockCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_id")
    @Comment("PK: 내부 식별자(숫자형)")
    private Long stockId;

    @Column(name = "ticker_krx", nullable = false, length = 6)
    @Comment("KRX 6자리 종목코드(예: 005930)")
    private String tickerKrx;

    @Column(name = "dart_corp_code", length = 8)
    @Comment("dart 종목 코드")
    private String dartCorpCode;

    @Column(name = "company_name", nullable = false, length = 200)
    @Comment("회사명(국문)")
    private String companyName;

    @Column(name = "industry", length = 200)
    @Comment("업종(수집원 기준 분류명)")
    private String industry;

    @Column(name = "listing_date")
    @Comment("상장일")
    private LocalDate listingDate;

    @Column(name = "fiscal_year_end_month")
    @Comment("결산월(1~12)")
    private Integer fiscalYearEndMonth;

    @Column(name = "homepage_url", length = 300)
    @Comment("회사 홈페이지 URL")
    private String homepageUrl;

    @Column(name = "region", length = 100)
    @Comment("지역(본사 소재지 등)")
    private String region;

    @Column(name = "market", length = 20)
    @Comment("시장(KOSPI/KOSDAQ/KONEX 등)")
    private String market;

    @Column(name = "isin", length = 20)
    @Comment("ISIN(국제증권식별번호, KRX JSON: ISU_CD)")
    private String isin;

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
