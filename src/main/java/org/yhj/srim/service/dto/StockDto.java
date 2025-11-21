package org.yhj.srim.service.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class StockDto {
    private Long stockId;
    private String tickerKrx;
    private String companyName;
    private String industry;
    private LocalDate listingDate;
    private String market;
    private String region;
    
    // Company 정보
    private Long companyId;
    private Long sharesOutstanding;
    private String sector;
}
