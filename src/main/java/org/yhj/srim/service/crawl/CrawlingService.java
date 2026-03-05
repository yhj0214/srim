package org.yhj.srim.service.crawl;

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
import org.yhj.srim.common.exception.code.StockError;
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


    public List<DartFsRow> crawlAnnualFinancial(String corpCode, int year) {

        List<DartFsRow> rows = dartClient.fetchAnnualFinancialStatements(corpCode, year);


        if(rows.isEmpty()) {
            log.warn("{}년도에 크롤링된 데이터가 없습니다.", year);
        }

        return rows;
    }


    /**
     * Dart 주식수(발행/자기/유통) 현황 크롤링 후 반환
     * @param company
     * @param year
     * @return
     */
    public List<DartShareStatusRow> crawlShareStatus(Company company, int year) {
        String corpCode = company.getStockCode().getDartCorpCode();
        return dartClient.fetchShareStatus(corpCode, year);
    }

    /**
     * DART 주식수(발행/자기/유통) 현황 크롤링 후 DB에 저장/업데이트
     */
    @Transactional
    public void crawlAndSaveShareStatus(String corpCode, Long companyId, int year) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(StockError.COMPANY_NOT_FOUND, "companyId=" + companyId));

        List<DartShareStatusRow> rows = dartClient.fetchShareStatus(corpCode, year);



        if(rows == null || rows.isEmpty()) {
            log.debug("주식 수 정보가 없습니다. corpCode={}, year={}", corpCode, year);
            return;
        }

        log.debug("DART 주식수 응답 {}건 - corpCode={}, year={}", rows.size(), corpCode, year);

        for (DartShareStatusRow row : rows) {

            // '비고' 행은 저장대상 아님
            if ("비고".equals(row.getSe())) {
                continue;
            }

            // 1) 사업연도 보정
            Integer bsnsYear = (row.getBsnsYear() != null) ? row.getBsnsYear() : year;
            String  se       = row.getSe();

            // 2) 유통주식수 계산
            Long istc  = row.getIstcTotqy();
            Long self  = row.getTesstkCo();
            Long distb = row.getDistbStockCo();
            if (distb == null && istc != null && self != null) {
                distb = istc - self;
            }

            // 3) 기존 데이터 조회 (companyId + bsnsYear + se)
            Optional<StockShareStatus> existingOpt =
                    shareStatusRepository.findByCompany_CompanyIdAndBsnsYearAndSe(companyId, bsnsYear, se);

            StockShareStatus entity;

            if (existingOpt.isPresent()) {
                // ── 이미 존재 → 같은 ID로 UPDATE용 엔티티 생성
                StockShareStatus existing = existingOpt.get();

                entity = StockShareStatus.builder()
                        .stockStatusId(existing.getStockStatusId()) // ★ ID 유지 → UPDATE
                        .company(existing.getCompany())             // 또는 company
                        .bsnsYear(bsnsYear)
                        .settlementDate(row.getStlmDt())
                        .se(se)
                        .isuStockTotqy(row.getIsuStockTotqy())
                        .istcTotqy(istc)
                        .tesstkCo(self)
                        .distbStockCo(distb)
                        .build();

                log.debug("기존 주식수 정보 업데이트 대상: {}", entity);

            } else {
                // ── 없는 경우 → 신규 INSERT
                entity = StockShareStatus.builder()
                        .company(company)
                        .bsnsYear(bsnsYear)
                        .settlementDate(row.getStlmDt())
                        .se(se)
                        .isuStockTotqy(row.getIsuStockTotqy())
                        .istcTotqy(istc)
                        .tesstkCo(self)
                        .distbStockCo(distb)
                        .build();

                log.debug("신규 주식수 정보 생성: {}", entity);
            }

            // JPA: id 있으면 UPDATE, 없으면 INSERT
            shareStatusRepository.save(entity);
        }

        log.debug("주식수 저장/업데이트 완료 - corpCode={}, companyId={}, year={}",
                corpCode, companyId, year);
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

    @Transactional
    public int crawlingStockPrice(Long companyId, LocalDate start, LocalDate end) {

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(StockError.COMPANY_NOT_FOUND));

        StockCode stockCode = company.getStockCode();

        String tickerKrx = stockCode.getTickerKrx();

        log.info("네이버 주가 크롤링 시작 - companyId={}, ticker={}, {} ~ {}",
                companyId, tickerKrx, start, end);

        List<DaliyPrice> daliyPrices = naverClient.fetchDailyPrices(tickerKrx, start, end);

        List<StockPrice> entities = daliyPrices.stream()
                .map(price -> StockPrice.builder()
                        .company(company)
                        .tradeDate(price.getDate())  // 거래일 설정 (필수!)
                        .asOf(LocalDateTime.now())   // 수집 시각
                        .price(price.getClose())
                        .openPrice(price.getOpen())
                        .highPrice(price.getHigh())
                        .lowPrice(price.getLow())
                        .volume(price.getVolume())
                        .source(StockPrice.MarketSnapshotSource.NAVER)
                        .build())
                .toList();

        stockPriceRepository.saveAll(entities);

        log.info("주가 수집 개수 : {}", entities.size());
        return entities.size();
    }

}
