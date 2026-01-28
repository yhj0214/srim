package org.yhj.srim.service.facade.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@ToString
public class YearBaseData {
    private final int year;                 // financialYear
    private final Long sharesOutstanding;    // 유통주식수
    private final BigDecimal roe;            // 가중평균 ROE
    private final BigDecimal equityOwner;    // 지배주주지분
}
