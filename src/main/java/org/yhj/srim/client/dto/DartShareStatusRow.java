package org.yhj.srim.client.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
public class DartShareStatusRow {


    String corpCode;       // corp_code
    int    businessYear;   // bsns_year
    String reportCode;     // reprt_code (11011 등)

    String se;             // 구분(보통주, 우선주, 합계 등)
    String isuDvdn;        // isu_dvdn (배당종류)
    String isuCd;          // isu_cd (종목코드)
    String isuAbbrv;       // isu_abbrv (종목명)

    BigDecimal thstrmStkQty;      // 당기 말 주식수
    BigDecimal frmtrmStkQty;      // 전기 말 주식수
    BigDecimal bfefrmtrmStkQty;   // 전전기 말 주식수


}
