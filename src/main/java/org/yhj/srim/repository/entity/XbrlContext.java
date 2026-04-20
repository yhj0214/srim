package org.yhj.srim.repository.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "xbrl_context")
@Comment("XBRL fact가 참조하는 기간/차원 context")
public class XbrlContext {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "xbrl_context_id")
    @Comment("XBRL context ID")
    private Long xbrlContextId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "xbrl_document_id", nullable = false)
    @Comment("소속 XBRL 문서")
    private XbrlDocument document;

    @Lob
    @Column(name = "context_ref", nullable = false)
    @Comment("XBRL context id")
    private String contextRef;

    @Column(name = "context_ref_hash", nullable = false, length = 64)
    @Comment("context_ref SHA-256 해시")
    private String contextRefHash;

    @Column(name = "entity_identifier", length = 500)
    @Comment("context에 선언된 entity identifier")
    private String entityIdentifier;

    @Column(name = "period_type", nullable = false, length = 10)
    @Comment("기간 유형(instant 또는 duration)")
    private String periodType;

    @Column(name = "period_start")
    @Comment("duration context 시작일")
    private LocalDate periodStart;

    @Column(name = "period_end")
    @Comment("duration context 종료일")
    private LocalDate periodEnd;

    @Column(name = "instant_date")
    @Comment("instant context 기준일")
    private LocalDate instantDate;

    @Lob
    @Column(name = "dimensions_json")
    @Comment("axis/member 원천 정보를 JSON으로 직렬화한 값")
    private String dimensionsJson;

    @Lob
    @Column(name = "member_signature")
    @Comment("axis/member 조합을 정렬한 비교용 시그니처")
    private String memberSignature;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Comment("행 생성 시각")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
