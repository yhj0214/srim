package org.yhj.srim.client.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@ToString
@NoArgsConstructor
@Setter
public class DartShareStatusRow {


    // 공시 메타
    private String rceptNo;    // 접수번호
    private String corpCls;    // 법인구분 (Y, K 등)
    private String corpCode;   // DART corp_code
    private String corpName;   // 회사명

    // 비즈니스 필드
    private Integer bsnsYear;   // 사업연도 (API 호출 연도 기준)
    private String se;         // 주식 종류(보통주/우선주/합계/비고 등)
    private LocalDate stlmDt;     // 결산일 (2023-12-31)

    // 정관상 한도 / 증감 관련
    private Long isuStockTotqy;       // 발행할 주식의 총수(정관상 한도)
    private Long nowToIsuStockTotqy;  // 당기 중 증가 주식수
    private Long nowToDcrsStockTotqy; // 당기 중 감소 주식수
    private Long redc;                // 감자
    private Long profitIncnr;         // 이익소각 등
    private Long rdmstkRepy;          // 상환/환매
    private Long etc;                 // 기타

    // 실제 발행/자기/유통 주식
    private Long istcTotqy;       // 발행주식의 총수
    private Long tesstkCo;        // 자기주식수
    private Long distbStockCo;    // 유통주식수

    // 디버깅용
    private String rawJson;
}