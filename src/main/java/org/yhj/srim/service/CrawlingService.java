package org.yhj.srim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.client.DartClient;
import org.yhj.srim.client.NaverClient;
import org.yhj.srim.client.dto.DaliyPrice;
import org.yhj.srim.client.dto.DartFsRow;
import org.yhj.srim.client.dto.DartShareStatusRow;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.ErrorCode;
import org.yhj.srim.common.exception.code.FinancialErrorCode;
import org.yhj.srim.common.exception.code.StockErrorCode;
import org.yhj.srim.repository.*;
import org.yhj.srim.repository.entity.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlingService {

    private final DartClient dartClient;
    private final NaverClient naverClient;
    private final DartFsFilingRepository filingRepository;
    private final DartFsLineRepository lineRepository;
    private final StockShareStatusRepository shareStatusRepository;
    private final CompanyRepository companyRepository;
    private final StockCodeRepository stockCodeRepository;
    private final StockPriceRepository stockPriceRepository;

    @Transactional
    public int crawlAndSaveAnnualFinancial(String corpCode, Long companyId, int year) {

        // 크롤링 결과
        List<DartFsRow> rows = dartClient.fetchAnnualFinancialStatements(corpCode, year);

        if(rows.isEmpty()) {
            log.warn("{}년도에 크롤링된 데이터가 없습니다.", year);
            return 0;
        }

        DartFsRow meta = rows.get(0);
        DartFsFiling filing = createOrGetFiling(corpCode, companyId, meta);

        // Line 엔티티로 변환 + 저장
        List<DartFsLine> entities = rows.stream()
                .map(row -> DartFsLine.fromRow(
                        filing,
                        companyId,
                        row
                ))
                .toList();

        lineRepository.saveAll(entities);
        return entities.size();
    }

    /**
     * DART 주식수(발행/자기/유통) 현황 크롤링 후 DB에 저장/업데이트
     */
    @Transactional
    public void crawlAndSaveShareStatus(String corpCode, Long companyId, int year) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found. companyId=" + companyId));

        List<DartShareStatusRow> rows = dartClient.fetchShareStatus(corpCode, year);


        for(DartShareStatusRow row : rows) {
            log.debug("행 정보 : {}", row);
        }

        if(rows == null || rows.isEmpty()) {
            log.debug("주식 수 정보가 없습니다. corpCode={}, year={}", corpCode, year);
            return;
        }

        log.debug("DART 주식수 응답 {}건 - corpCode={}, year={}", rows.size(), corpCode, year);

        List<StockShareStatus> entities = rows.stream()
                .filter(row -> !"비고".equals(row.getSe()))
                .map(row -> {

                    Integer bsnsYear = row.getBsnsYear() != null ? row.getBsnsYear() : year;

                    Long istc  = row.getIstcTotqy();
                    Long self  = row.getTesstkCo();
                    Long distb = row.getDistbStockCo();
                    if (distb == null && istc != null && self != null) {
                        distb = istc - self;
                    }

                    return StockShareStatus.builder()
                            .company(company)
                            .bsnsYear(bsnsYear)
                            .settlementDate(row.getStlmDt())
                            .se(row.getSe())
                            .isuStockTotqy(row.getIsuStockTotqy())
                            .istcTotqy(row.getIstcTotqy())
                            .tesstkCo(row.getTesstkCo())
                            .distbStockCo(distb)
                            .build();
                })
                .toList();

        for(StockShareStatus entity : entities) {
            log.debug("저장할 주식수 정보: {}", entity);
        }
        shareStatusRepository.saveAll(entities);

        log.debug("주식수 {}건 저장 완료 - corpCode={}, companyId={}, year={}",
                entities.size(), corpCode, companyId, year);

    }

    private DartFsFiling createOrGetFiling(String corpCode, Long companyId, DartFsRow firstRow) {
        String rceptNo = firstRow.getRceptNo();
        String reprtCode = firstRow.getReprtCode();
        String fsDiv = firstRow.getFsDiv();

        Optional<DartFsFiling> existingOpt = filingRepository.findByRceptNoAndReprtCodeAndFsDiv(rceptNo, reprtCode, fsDiv);
        if(existingOpt.isPresent()) {
            DartFsFiling existing = existingOpt.get();
            return existing;
        }

        LocalDate rceptDt = null;
        String rceptDtStr = firstRow.getRceptDt(); // "20230320" 같은 형식이라고 가정
        if (rceptDtStr != null && rceptDtStr.length() == 8) {
            int yyyy = Integer.parseInt(rceptDtStr.substring(0, 4));
            int mm   = Integer.parseInt(rceptDtStr.substring(4, 6));
            int dd   = Integer.parseInt(rceptDtStr.substring(6, 8));
            rceptDt = LocalDate.of(yyyy, mm, dd);
        }

        DartFsFiling filing = DartFsFiling.builder()
                .corpCode(corpCode)
                .companyId(companyId)
                .rceptNo(rceptNo)
                .reprtCode(reprtCode)
                .bsnsYear(firstRow.getBsnsYear())
                .fsDiv(fsDiv)
                .reportTp("연간")
                .currency(firstRow.getCurrency())
                .build();

        return filingRepository.save(filing);
    }

    public int crawlingStockPrice(Long companyId, LocalDate start, LocalDate end) {

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(StockErrorCode.COMPANY_NOT_FOUND));

        StockCode stockCode = company.getStockCode();

        String tickerKrx = stockCode.getTickerKrx();

        log.info("네이버 주가 크롤링 시작 - companyId={}, ticker={}, {} ~ {}",
                companyId, tickerKrx, start, end);

        List<DaliyPrice> daliyPrices = naverClient.fetchDailyPrices(tickerKrx, start, end);

        List<StockPrice> entities = daliyPrices.stream()
                .map(price -> StockPrice.builder()
                        .company(company)
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
        return entities.size();
    }
}
