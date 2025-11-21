package org.yhj.srim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.repository.CompanyRepository;
import org.yhj.srim.repository.StockCodeRepository;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.StockCode;
import org.yhj.srim.service.dto.StockDto;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class StockService {

    private final StockCodeRepository stockCodeRepository;
    private final CompanyRepository companyRepository;

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
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("종목을 찾을 수 없습니다. (market=%s, ticker=%s)", market, ticker)
                ));
        
        return toDto(stockCode);
    }

    /**
     * Stock ID로 종목 조회
     */
    public StockDto getById(Long stockId) {
        log.debug("종목 조회: stockId={}", stockId);
        
        StockCode stockCode = stockCodeRepository.findById(stockId)
                .orElseThrow(() -> new IllegalArgumentException("종목을 찾을 수 없습니다. ID: " + stockId));
        
        return toDto(stockCode);
    }

    /**
     * 전체 종목 수 조회
     */
    public long count() {
        return stockCodeRepository.count();
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
}
