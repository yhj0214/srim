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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.stream.Collectors.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class StockPriceService {

    private final CompanyRepository companyRepository;
    private final StockPriceRepository stockPriceRepository;

    public List<Company> findAllCompaniesWithStockCode() {
        return companyRepository.findAllWithStockCode();
    }

    @Transactional
    public int savePrices(Long companyId, List<DaliyPrice> dailyPrices) {
        if (dailyPrices.isEmpty()) {
            return 0;
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(StockError.COMPANY_NOT_FOUND, "companyId=" + companyId));
        String tickerKrx = company.getStockCode().getTickerKrx();
        LocalDateTime collectedAt = LocalDateTime.now();

        Map<LocalDate, DaliyPrice> latestByDate = new LinkedHashMap<>();
        for (DaliyPrice price : dailyPrices) {
            if (price == null || price.getDate() == null) {
                continue;
            }
            latestByDate.put(price.getDate(), price);
        }

        if (latestByDate.isEmpty()) {
            return 0;
        }

        LocalDate minDate = latestByDate.keySet().stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate maxDate = latestByDate.keySet().stream().max(LocalDate::compareTo).orElseThrow();

        List<StockPrice> existingPrices = stockPriceRepository
                .findByCompany_CompanyIdAndTradeDateBetweenOrderByTradeDateAsc(
                        companyId,
                        minDate,
                        maxDate
                );

        Map<LocalDate, StockPrice> existingByDate = new LinkedHashMap<>();
        for (StockPrice existingPrice : existingPrices) {
            existingByDate.putIfAbsent(existingPrice.getTradeDate(), existingPrice);
        }

        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        for (DaliyPrice price : latestByDate.values()) {
            StockPrice existing = existingByDate.get(price.getDate());
            if (existing != null) {
                if (hasChanges(existing, price)) {
                    existing.updateDailySnapshot(
                            collectedAt,
                            price.getClose(),
                            price.getOpen(),
                            price.getHigh(),
                            price.getLow(),
                            price.getVolume(),
                            StockPrice.MarketSnapshotSource.NAVER
                    );
                    updated++;
                } else {
                    skipped++;
                }
                continue;
            }

            stockPriceRepository.save(StockPrice.builder()
                    .company(company)
                    .tradeDate(price.getDate())
                    .asOf(collectedAt)
                    .price(price.getClose())
                    .openPrice(price.getOpen())
                    .highPrice(price.getHigh())
                    .lowPrice(price.getLow())
                    .volume(price.getVolume())
                    .source(StockPrice.MarketSnapshotSource.NAVER)
                    .build());
            inserted++;
        }

        log.info("NAVER 주가 저장 완료 - companyId={}, ticker={}, inserted={}, updated={}, skipped={}",
                companyId, tickerKrx, inserted, updated, skipped);
        return latestByDate.size();
    }

    private boolean hasChanges(StockPrice existing, DaliyPrice incoming) {
        return !Objects.equals(existing.getPrice(), incoming.getClose())
                || !Objects.equals(existing.getOpenPrice(), incoming.getOpen())
                || !Objects.equals(existing.getHighPrice(), incoming.getHigh())
                || !Objects.equals(existing.getLowPrice(), incoming.getLow())
                || !Objects.equals(existing.getVolume(), incoming.getVolume());
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
