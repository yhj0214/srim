package org.yhj.srim.repository.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "company")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Comment("회사 메타(상장주식수/액면가/통화 등)")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "company_id")
    @Comment("PK: 내부 식별자(숫자형)")
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    @Comment("FK → stock_code.stock_id")
    private StockCode stockCode;

    @Column(name = "shares_outstanding")
    @Comment("상장/유통 주식수(기본 단위: 주)")
    private Long sharesOutstanding;

    @Column(name = "face_value", precision = 15, scale = 2)
    @Comment("액면가(원)")
    private BigDecimal faceValue;

    @Column(name = "currency", nullable = false, length = 3)
    @Comment("표준 통화코드(기본 KRW)")
    @Builder.Default
    private String currency = "KRW";

    @Column(name = "sector", length = 100)
    @Comment("섹터(대분류, 선택)")
    private String sector;

    @Column(name = "notes", length = 500)
    @Comment("비고")
    private String notes;

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
        if (currency == null) {
            currency = "KRW";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void updateSharesOutstanding(Long shares) {
        this.sharesOutstanding = shares;
    }
}
