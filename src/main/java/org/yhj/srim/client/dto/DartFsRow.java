package org.yhj.srim.client.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@ToString
@Setter
public class DartFsRow {

    // 공시 공통 헤더 영역
    private String rceptNo;      // 접수번호
    private String reprtCode;    // 보고서 코드(11011 사업보고서 등)
    private int    bsnsYear;     // 사업연도
    private String fsDiv;        // 재무제표 구분 CFS/OFS 등
    private String rceptDt;      // 접수일 (YYYYMMDD 문자열 그대로)

    // 라인 단위 영역
    private String sjDiv;        // BS/CIS/CF
    private String sjNm;         // 재무상태표 / 포괄손익계산서 등

    private String accountId;
    private String accountNm;
    private String accountDetail;

    private Integer ord;

    private String     thstrmNm;
    private BigDecimal thstrmAmount;
    private BigDecimal thstrmAddAmount;

    private String     frmtrmNm;
    private BigDecimal frmtrmAmount;

    private String     bfefrmtrmNm;
    private BigDecimal bfefrmtrmAmount;

    private String currency;

    // 디버깅용 원본 JSON (있으면 좋고, 없어도 무방)
    private String rawJson;

    // Jackson 기본 생성자 필요
    public DartFsRow() {
    }

}
