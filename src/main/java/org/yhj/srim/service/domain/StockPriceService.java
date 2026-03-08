package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.client.dto.DaliyPrice;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.StockError;
import org.yhj.srim.repository.CompanyRepository;
import org.yhj.srim.repository.StockPriceRepository;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.StockPrice;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class StockPriceService {

    private final CompanyRepository companyRepository;
    private final StockPriceRepository stockPriceRepository;

    @Transactional
    public int savePrices(Long companyId, List<DaliyPrice> dailyPrices) {
        if (dailyPrices.isEmpty()) {
            return 0;
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(StockError.COMPANY_NOT_FOUND, "companyId=" + companyId));
        String tickerKrx = company.getStockCode().getTickerKrx();

        List<StockPrice> entities = dailyPrices.stream()
                .map(price -> StockPrice.builder()
                        .company(company)
                        .tradeDate(price.getDate())
                        .asOf(LocalDateTime.now())
                        .price(price.getClose())
                        .openPrice(price.getOpen())
                        .highPrice(price.getHigh())
                        .lowPrice(price.getLow())
                        .volume(price.getVolume())
                        .source(StockPrice.MarketSnapshotSource.NAVER)
                        .build())
                .toList();

        stockPriceRepository.saveAll(entities);
        log.info("NAVER 주가 저장 완료 - companyId={}, ticker={}, count={}", companyId, tickerKrx, entities.size());
        return entities.size();
    }


    public boolean existsByCompanyId(Long companyId) {
        return stockPriceRepository.existsByCompany_CompanyId(companyId);
    }

    public LocalDate findMinTradeDateByCompany(Long companyId) {
        return stockPriceRepository.findMinTradeDateByCompany(companyId);
    }

    public LocalDate findMaxTradeDateByCompany(Long companyId) {
        return stockPriceRepository.findMaxTradeDateByCompany(companyId);
    }

    public List<StockPrice> findPricesByCompanyIdAndTradeDateBetween(Long companyId, LocalDate start, LocalDate end) {
        return stockPriceRepository.findByCompany_CompanyIdAndTradeDateBetweenOrderByTradeDateAsc(companyId, start, end);
    }

    public String findTickerKrxByCompanyId(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(StockError.COMPANY_NOT_FOUND, "companyId=" + companyId));
        return company.getStockCode().getTickerKrx();
    }
}
