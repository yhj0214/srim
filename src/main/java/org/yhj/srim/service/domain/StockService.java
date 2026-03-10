package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.client.dto.DartShareStatusRow;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.StockError;
import org.yhj.srim.repository.CompanyRepository;
import org.yhj.srim.repository.StockCodeRepository;
import org.yhj.srim.repository.StockShareStatusRepository;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.StockCode;
import org.yhj.srim.repository.entity.StockShareStatus;
import org.yhj.srim.service.crawl.dto.StockCodeDraft;
import org.yhj.srim.service.dto.StockDto;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class StockService {

    private final StockCodeRepository stockCodeRepository;
    private final CompanyRepository companyRepository;
    private final StockShareStatusRepository stockShareStatusRepository;

    /**
     * 키워드로 종목 검색 (회사명 또는 티커)
     */
    public Page<StockDto> search(String keyword, Pageable pageable) {
        log.debug("종목 검색: keyword={}, page={}", keyword, pageable.getPageNumber());
        
        if (keyword == null || keyword.trim().isEmpty()) {
            return stockCodeRepository.findAll(pageable)
                    .map(this::toDto);
        }
        
        return stockCodeRepository.searchByKeyword(keyword.trim(), pageable)
                .map(this::toDto);
    }

    /**
     * 시장과 티커로 종목 상세 조회
     */
    public StockDto getByTicker(String market, String ticker) {
        log.debug("종목 조회: market={}, ticker={}", market, ticker);
        
        StockCode stockCode = stockCodeRepository.findByMarketAndTickerKrx(market, ticker)
                .orElseThrow(() -> new CustomException(
                        StockError.STOCK_NOT_FOUND,
                        String.format("market=%s, ticker=%s", market, ticker)
                ));
        
        return toDto(stockCode);
    }

    /**
     * Stock ID로 종목 조회
     */
    public StockDto getById(Long stockId) {
        log.debug("종목 조회: stockId={}", stockId);
        
        StockCode stockCode = stockCodeRepository.findById(stockId)
                .orElseThrow(() -> new CustomException(StockError.STOCK_NOT_FOUND, "stockId=" + stockId));
        
        return toDto(stockCode);
    }

    /**
     * 전체 종목 수 조회
     */
    public long count() {
        return stockCodeRepository.count();
    }

    public List<Long> findAllStockIds() {
        return stockCodeRepository.findAllStockIds();
    }

    public Long getStockIdByTickerKrx(String tickerKrx) {
        return stockCodeRepository.findStockIdByTickerKrx(tickerKrx)
                .orElseThrow(() -> new CustomException(StockError.STOCK_NOT_FOUND));
    }

    /**
     * Entity를 DTO로 변환
     */
    private StockDto toDto(StockCode stockCode) {
        StockDto dto = StockDto.builder()
                .stockId(stockCode.getStockId())
                .tickerKrx(stockCode.getTickerKrx())
                .companyName(stockCode.getCompanyName())
                .industry(stockCode.getIndustry())
                .listingDate(stockCode.getListingDate())
                .market(stockCode.getMarket())
                .region(stockCode.getRegion())
                .build();
        
        // Company 정보가 있으면 추가
        companyRepository.findByStockCode_StockId(stockCode.getStockId())
                .ifPresent(company -> {
                    dto.setCompanyId(company.getCompanyId());
                    dto.setSharesOutstanding(company.getSharesOutstanding());
                    dto.setSector(company.getSector());
                });
        
        return dto;
    }

    @Transactional
    public int saveStockDrafts(List<StockCodeDraft> drafts) {
        if(drafts == null || drafts.size() == 0) {
            return 0;
        }

        HashSet<String> dedup = new LinkedHashSet<>();
        List<StockCode> entities = new ArrayList<>();
        for(StockCodeDraft d : drafts) {
            if(d == null) continue;

            String ticker = d.getTickerKrx();
            if(ticker == null) continue;

            ticker = ticker.trim();
            if(ticker.isBlank()) continue;

            if(dedup.contains(ticker)) continue;

            dedup.add(ticker);
            StockCode stockCode = StockCode.of(d);
            entities.add(stockCode);
        }

        stockCodeRepository.saveAll(entities);

        return entities.size();
    }

    @Transactional
    public void replaceShareStatus(Company company, int year, List<DartShareStatusRow> rows) {

        if(rows == null || rows.isEmpty()) return;

        Long companyId = company.getCompanyId();

        long deleted = stockShareStatusRepository.deleteByCompany_CompanyIdAndBsnsYear(companyId, year);


        Map<String, DartShareStatusRow> dedupBySe = new LinkedHashMap<>();
        for (DartShareStatusRow r : rows) {
            if (r.getBsnsYear() != null && r.getBsnsYear() != year) {
                log.warn("DART shareStatus bsnsYear mismatch: requestedYear={}, rowYear={}, corpCode={}",
                        year, r.getBsnsYear(), company.getStockCode().getDartCorpCode());
            }
            if (r == null) continue;
            if ("비고".equals(r.getSe())) continue;

            String se = r.getSe();
            if (se == null || se.isBlank()) continue;

            // 이 메서드는 'year'만 다룬다 → bsnsYear는 year로 강제
            // (row.getBsnsYear()는 무시하거나, 다르면 로그만 남기는 것도 가능)
            dedupBySe.put(se, r);
        }

        if (dedupBySe.isEmpty()) return;

        List<StockShareStatus> entities = dedupBySe.values().stream()
                .map(r -> r.toEntity(company, year, r)) // toEntity는 int year 받도록 권장
                .toList();


        stockShareStatusRepository.saveAll(entities);
        log.debug("회사ID {} - {}년도 주식수 교체 완료 (deleted={}, inserted={})",
                companyId, year, deleted, entities.size());
    }
}
