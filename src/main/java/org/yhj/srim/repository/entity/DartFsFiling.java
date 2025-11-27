package org.yhj.srim.repository.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "dart_fs_filing")
@Builder
@AllArgsConstructor
public class DartFsFiling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fs_filing_id")
    private Long fsFilingId;

    // DART corp_code
    @Column(name = "corp_code", length = 8, nullable = false)
    @Comment("다트 식별코드")
    private String corpCode;

    @Column(name = "company_id")
    @Comment("회사 id")
    private Long companyId;

    @Column(name = "rcept_no", length = 14, nullable = false)
    @Comment("DART접수번호, 보고서식별")
    private String rceptNo;

    @Column(name = "reprt_code", length = 5, nullable = false)
    @Comment("보고서코드")
    private String reprtCode;

    @Column(name = "bsns_year", nullable = false)
    @Comment("사업연도")
    private Integer bsnsYear;

    @Column(name = "fs_div", length = 4)
    @Comment("재무제표 구분 CFS(연결)/OFS(개별)")
    private String fsDiv;

    @Column(name = "report_tp", length = 20)
    @Comment("보고서타입, 연간/분기")
    private String reportTp;

    @Column(name = "rcept_dt")
    @Comment("공시 접수일")
    private LocalDate rceptDt;

    @Column(name = "currency", length = 3)
    @Comment("통화")
    private String currency;

    @Column(name = "note", length = 300)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
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


}