package org.yhj.srim.repository.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.yhj.srim.client.dto.DartFsRow;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "dart_fs_line")
public class DartFsLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fs_line_id")
    private Long fsLineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fs_filing_id")
    @Comment("보고서 단위")
    private DartFsFiling filing;


    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "sj_div", length = 4, nullable = false)
    @Comment("재무제표 구분 코드 ex) BS, IS, CIS, CF 등")
    private String sjDiv;

    @Column(name = "sj_nm", length = 200)
    @Comment("재무제표 구분 이름 ex) 재무상태표, 손익 등..")
    private String sjNm;

    @Column(name = "account_id", length = 300, nullable = false)
    @Comment("재무제표 계정 Id, ex) ifrs-full_CurrentAssets..")
    private String accountId;

    @Column(name = "account_nm", length = 200)
    @Comment("재무제표 계정 이름, ex) 유동자산")
    private String accountNm;

    @Column(name = "account_detail", length = 200)
    @Comment("추가 설명, 세무항목 없는경우 - 표시")
    private String accountDetail;

    @Column(name = "ord")
    @Comment("Dart응답의 출력순서")
    private Integer ord;

    @Column(name = "thstrm_nm", length = 50)
    @Comment("당기 라벨 ex) 제 57기")
    private String thstrmNm;

    @Column(name = "thstrm_amount", precision = 28, scale = 0)
    @Comment("당기 금액")
    private BigDecimal thstrmAmount;

    @Column(name = "thstrm_add_amount", precision = 28, scale = 0)
    @Comment("당기 추가금액")
    private BigDecimal thstrmAddAmount;

    @Column(name = "frmtrm_nm", length = 50)
    @Comment("전기 라벨 ex) 제 57기")
    private String frmtrmNm;

    @Column(name = "frmtrm_amount", precision = 28, scale = 0)
    @Comment("전기 금액")
    private BigDecimal frmtrmAmount;

    @Column(name = "bfefrmtrm_nm", length = 50)
    @Comment("전전기 라벨 ex) 제 57기")
    private String bfefrmtrmNm;

    @Column(name = "bfefrmtrm_amount", precision = 28, scale = 0)
    @Comment("전전기 금액")
    private BigDecimal bfefrmtrmAmount;

    @Column(name = "currency", length = 3)
    @Comment("통화코드")
    private String currency;

    @Column(name = "row_hash", length = 64)
    @Comment("현재 라인 동일 여부 확인 해시값")
    private String rowHash;

    @Lob
    @Column(name = "원본데이터 보관용도")
    private String rawJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static DartFsLine fromRow(DartFsFiling fsFiling, Long companyId, DartFsRow row) {
        DartFsLine entity = new DartFsLine();
        entity.filing = fsFiling;
        entity.companyId = companyId;
        entity.sjDiv = row.getSjDiv();
        entity.sjNm = row.getSjNm();
        entity.accountId = row.getAccountId();
        entity.accountNm = row.getAccountNm();
        entity.accountDetail = row.getAccountDetail();
        entity.ord = row.getOrd();
        entity.thstrmNm = row.getThstrmNm();
        entity.thstrmAmount = row.getThstrmAmount();
        entity.thstrmAddAmount = row.getThstrmAddAmount();
        entity.frmtrmNm = row.getFrmtrmNm();
        entity.frmtrmAmount = row.getFrmtrmAmount();
        entity.bfefrmtrmNm = row.getBfefrmtrmNm();
        entity.bfefrmtrmAmount = row.getBfefrmtrmAmount();
        entity.currency = row.getCurrency();
//        entity.rowHash = rowHash;
        entity.rawJson = row.getRawJson();
        return entity;
    }
}