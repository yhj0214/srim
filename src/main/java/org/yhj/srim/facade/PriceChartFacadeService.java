package org.yhj.srim.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.service.CrawlingService;
import org.yhj.srim.service.StockPriceService;
import org.yhj.srim.service.dto.StockPriceDto;

import java.time.LocalDate;

@Service
@Slf4j
@RequiredArgsConstructor
public class PriceChartFacadeService {

    private static final int INITIAL_BACKfILL_YEARS = 10;
    private final StockPriceService stockPriceService;
    private final CrawlingService crawlingService;


    public StockPriceDto getPriceChart(Long companyId, LocalDate startDate, LocalDate endDate) {

        LocalDate end = (endDate != null) ? endDate : LocalDate.now();
        LocalDate start = (startDate != null) ? startDate : end.minusYears(1);

        log.info("PriceChart 조회 기간 - start={}, end={}", start, end);

        // 주식 가격 확보
        ensurePriceData(companyId, start, end);

        // DB 다시 조회

        // srim 조회

        // DTO 반환

        return null;
    }

    /**
     *  - 첫 조회 시 : 최근 10년치 등록
     *  - 이후 부족한 구간 크롤링
     */
    private void ensurePriceData(Long companyId, LocalDate start, LocalDate end) {

        boolean hasData = stockPriceService.hasAnyPrice(companyId);

        // 최초 조회인경우 end로부터 10년 조회
        if(!hasData) {
            LocalDate backfillStart = end.minusYears(INITIAL_BACKfILL_YEARS);
            log.info("주가 데이터 최초 조회. companyId={} → {} ~ {} 10년치 백필 크롤링",
                    companyId, backfillStart, end);

            crawlingService.crawlingStockPrice(companyId, backfillStart, end);
            return;
        }
    }
}
