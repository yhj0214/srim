package org.yhj.srim.service.dto;

import lombok.*;
import org.yhj.srim.repository.entity.Company;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PopularStockDto {
    private Long companyId;
    private Long stockId;
    private String tickerKrx;
    private String companyName;
    private String market;
    private Long viewCount;

    public static PopularStockDto from(Company company, Long viewCount) {
        return PopularStockDto.builder()
                .companyId(company.getCompanyId())
                .stockId(company.getStockCode().getStockId())
                .tickerKrx(company.getStockCode().getTickerKrx())
                .companyName(company.getStockCode().getCompanyName())
                .market(company.getStockCode().getMarket())
                .viewCount(viewCount)
                .build();
    }
}
