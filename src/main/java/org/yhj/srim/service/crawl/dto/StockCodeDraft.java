package org.yhj.srim.service.crawl.dto;

import lombok.*;
import org.yhj.srim.repository.entity.StockCode;

import java.time.LocalDate;


@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
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
