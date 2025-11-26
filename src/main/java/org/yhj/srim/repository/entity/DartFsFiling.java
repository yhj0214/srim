package org.yhj.srim.repository.entity;

import jakarta.persistence.*;
import lombok.*;

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
    private String corpCode;

    // 회사 FK
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "rcept_no", length = 14, nullable = false)
    private String rceptNo;

    @Column(name = "reprt_code", length = 5, nullable = false)
    private String reprtCode;

    @Column(name = "bsns_year", nullable = false)
    private Integer bsnsYear;

    @Column(name = "fs_div", length = 4)
    private String fsDiv;

    @Column(name = "report_tp", length = 20)
    private String reportTp;

    @Column(name = "rcept_dt")
    private LocalDate rceptDt;

    @Column(name = "currency", length = 3)
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