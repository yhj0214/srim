package org.yhj.srim.client.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@ToString
@Setter
@NoArgsConstructor
public class DartFsRow {

    // 공시 공통 헤더 영역
    private String rceptNo;         // 접수번호
    private String reprtCode;       // 보고서 코드(11011, 11012, 11013), 11011만 다룸
    private int    bsnsYear;        // 사업연도
    private String fsDiv;           // 재무제표 구분 CFS(연결)/OFS(개별)
    private String rceptDt;         // 접수일 (YYYYMMDD 문자열 그대로)

    // 라인 단위 영역, BS(재무상태표)/CIS(포괄손익계산서)/CF(현금흐름표)
    private String sjDiv;           // 재무제표 종류
    private String sjNm;            // 재무제표 이름

    private String accountId;       // 계정구분 표준코드 (ifrs-full_Revenue, ifrs_full_profitLoss 등)
    private String accountNm;       // 계정코드 한글명 (매출액, 영업이익 등)
    private String accountDetail;   // 계정에 대한 추가설명, 세부 분류, "-"인 경우 많음

    private Integer ord;            // 표시순서

    private String     thstrmNm;    // 당기 칼럼 라벨 ("제 xx 기")
    private BigDecimal thstrmAmount;    // 당기 금액
    private BigDecimal thstrmAddAmount; // 당기 누적 금액

    private String     frmtrmNm;    // 전기 라벨
    private BigDecimal frmtrmAmount;    // 전기 금액

    private String     bfefrmtrmNm; // 전전기 라벨
    private BigDecimal bfefrmtrmAmount; // 전전기 금액

    private String currency;        // 단위 화폐 ("KRW", "USD" 등)

    private String rawJson;         // 원본 JSON문자열, 디버깅 용도

}
