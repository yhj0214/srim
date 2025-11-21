package org.yhj.srim.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CompanyMetaDto {
    private Long sharesOutstanding; // 상장주식수
    private BigDecimal faceValue;   // 액면가
    private String sector;          // 섹터(대분류)
    private String currency;        // 통화 (없으면 KRW 기본)
    private String notes;           // 비고(필요하면)
}