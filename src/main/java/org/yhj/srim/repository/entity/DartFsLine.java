package org.yhj.srim.repository.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
    private Long id;

    @Column(name = "fs_filing_id", nullable = false)
    private Long fsFilingId;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "sj_div", length = 4, nullable = false)
    private String sjDiv;

    @Column(name = "sj_nm", length = 200)
    private String sjNm;

    @Column(name = "account_id", length = 200, nullable = false)
    private String accountId;

    @Column(name = "account_nm", length = 200)
    private String accountNm;

    @Column(name = "account_detail", length = 200)
    private String accountDetail;

    @Column(name = "ord")
    private Integer ord;

    @Column(name = "thstrm_nm", length = 50)
    private String thstrmNm;

    @Column(name = "thstrm_amount", precision = 28, scale = 0)
    private BigDecimal thstrmAmount;

    @Column(name = "thstrm_add_amount", precision = 28, scale = 0)
    private BigDecimal thstrmAddAmount;

    @Column(name = "frmtrm_nm", length = 50)
    private String frmtrmNm;

    @Column(name = "frmtrm_amount", precision = 28, scale = 0)
    private BigDecimal frmtrmAmount;

    @Column(name = "bfefrmtrm_nm", length = 50)
    private String bfefrmtrmNm;

    @Column(name = "bfefrmtrm_amount", precision = 28, scale = 0)
    private BigDecimal bfefrmtrmAmount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "row_hash", length = 64)
    private String rowHash;

    @Lob
    @Column(name = "raw_json")
    private String rawJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static DartFsLine fromRow(Long fsFilingId, Long companyId, DartFsRow row) {
        DartFsLine entity = new DartFsLine();
        entity.fsFilingId = fsFilingId;
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