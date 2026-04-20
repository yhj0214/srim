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
@Table(name = "xbrl_fact")
@Comment("XBRL 개별 fact 원천 데이터")
public class XbrlFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "xbrl_fact_id")
    @Comment("XBRL fact ID")
    private Long xbrlFactId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "xbrl_document_id", nullable = false)
    @Comment("소속 XBRL 문서")
    private XbrlDocument document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "xbrl_context_id")
    @Comment("참조 context")
    private XbrlContext context;

    @Lob
    @Column(name = "context_ref")
    @Comment("원문 fact의 contextRef")
    private String contextRef;

    @Column(name = "concept_qname", nullable = false, length = 700)
    @Comment("개념 QName(ifrs-full:Revenue 등)")
    private String conceptQname;

    @Column(name = "concept_local_name", nullable = false, length = 500)
    @Comment("개념 local name(Revenue 등)")
    private String conceptLocalName;

    @Column(name = "label_ko", length = 1000)
    @Comment("한글 라벨 캐시(현재는 비어 있을 수 있음)")
    private String labelKo;

    @Column(name = "statement_role", length = 1000)
    @Comment("statement role 또는 원본 entry 식별자")
    private String statementRole;

    @Column(name = "unit_ref", length = 255)
    @Comment("원문 fact의 unitRef")
    private String unitRef;

    @Column(name = "decimals", length = 100)
    @Comment("원문 fact의 decimals 속성")
    private String decimals;

    @Lob
    @Column(name = "value_raw")
    @Comment("원문 fact 문자열 값")
    private String valueRaw;

    @Column(name = "value_numeric", precision = 28, scale = 6)
    @Comment("숫자형으로 변환한 fact 값")
    private BigDecimal valueNumeric;

    @Column(name = "is_nil", nullable = false)
    @Comment("xsi:nil 여부")
    private boolean isNil;

    @Lob
    @Column(name = "member_signature")
    @Comment("context 차원 시그니처 복제본")
    private String memberSignature;

    @Column(name = "order_hint")
    @Comment("문서 순회 시 부여한 순서 힌트")
    private Integer orderHint;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Comment("행 생성 시각")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
