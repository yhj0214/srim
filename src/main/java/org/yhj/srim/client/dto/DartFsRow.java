package org.yhj.srim.client.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Builder
@Value
public class DartFsRow {

    String corpCode;
    int    businessYear;
    String reportCode;    // reprt_code (11011 = 사업보고서)
    String fsType;        // sj_div (BS, IS, CIS 등)
    String fsName;        // sj_nm

    String accountId;     // account_id (ifrs-full_...)
    String accountName;   // account_nm

    BigDecimal currentAmount; // thstrm_amount
    BigDecimal priorAmount;   // frmtrm_amount
    String currency;          // KRW 등
}
