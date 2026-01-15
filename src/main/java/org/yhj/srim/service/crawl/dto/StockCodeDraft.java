package org.yhj.srim.service.crawl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;


@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StockCodeDraft {
    private String market;
    private String tickerKrx;
    private String companyName;
    private String industry;
    private LocalDate listingDate;
    private String region;
    private String homepageUrl;
    private Integer fiscalYearEndMonth;

}
