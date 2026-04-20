package org.yhj.srim.repository.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "xbrl_document")
@Comment("DART XBRL 원문 문서 메타 데이터")
public class XbrlDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "xbrl_document_id")
    @Comment("XBRL 문서 ID")
    private Long xbrlDocumentId;

    @Column(name = "corp_code", nullable = false, length = 8)
    @Comment("DART corp_code")
    private String corpCode;

    @Column(name = "company_id")
    @Comment("내부 회사 ID")
    private Long companyId;

    @Column(name = "rcept_no", nullable = false, length = 14)
    @Comment("DART 접수번호")
    private String rceptNo;

    @Column(name = "reprt_code", nullable = false, length = 5)
    @Comment("보고서 코드")
    private String reprtCode;

    @Column(name = "bsns_year", nullable = false)
    @Comment("사업연도")
    private Integer bsnsYear;

    @Column(name = "fs_div", nullable = false, length = 4)
    @Comment("재무제표 구분(CFS/OFS 등)")
    private String fsDiv;

    @Column(name = "report_tp", length = 50)
    @Comment("보고서 타입(연간/반기/분기)")
    private String reportTp;

    @Column(name = "source_url", length = 1000)
    @Comment("DART XBRL 다운로드 URL")
    private String sourceUrl;

    @Column(name = "local_path", length = 1000)
    @Comment("저장된 원문 zip 파일 경로")
    private String localPath;

    @Column(name = "taxonomy_version", length = 1000)
    @Comment("파싱 시 확인한 taxonomy 버전")
    private String taxonomyVersion;

    @Column(name = "parse_version", nullable = false, length = 100)
    @Comment("이 문서를 적재한 파서 버전")
    private String parseVersion;

    @Column(name = "parsed_at")
    @Comment("원문 파싱 완료 시각")
    private LocalDateTime parsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Comment("행 생성 시각")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Comment("행 수정 시각")
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
