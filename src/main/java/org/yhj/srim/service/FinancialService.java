package org.yhj.srim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.StockErrorCode;
import org.yhj.srim.controller.dto.CompanyMetaDto;
import org.yhj.srim.repository.*;
import org.yhj.srim.repository.entity.*;
import org.yhj.srim.service.dto.FinancialTableDto;
import org.yhj.srim.service.dto.PeriodType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FinancialService {

    private final FinPeriodRepository finPeriodRepository;
    private final FinMetricDefRepository finMetricDefRepository;
    private final FinMetricValueRepository finMetricValueRepository;
    private final CompanyRepository companyRepository;
    private final StockCodeRepository stockCodeRepository;

    /**
     * stockId로 연간 재무 테이블 조회
     */
    // to-do 리팩토링 완료 후 삭제
    @Transactional
    public FinancialTableDto getAnnualTableByStockId(Long stockId, int limit) {
        log.info("=== getAnnualTableByStockId 호출 ===");
        log.info("stockId: {}, limit: {}", stockId, limit);

        // Company 가져오기 또는 생성
        Company company = getOrCreateCompany(stockId);
        log.info("Company 조회/생성 완료: companyId={}", company.getCompanyId());

        // 재무 데이터 조회 (크롤링 포함)
        return getFinancialTable(company, limit, PeriodType.ANNUAL);
    }

    /**
     * stockId로 분기 재무 테이블 조회
     */
//    @Transactional
//    public FinancialTableDto getQuarterTableByStockId(Long stockId, int limit) {
//        log.info("=== getQuarterTableByStockId 호출 ===");
//        log.info("stockId: {}, limit: {}", stockId, limit);
//
//        // Company 가져오기 또는 생성
//        Company company = getOrCreateCompany(stockId);
//        log.info("Company 조회/생성 완료: companyId={}", company.getCompanyId());
//
//        // 재무 데이터 조회 (크롤링 포함)
//        return getFinancialTable(company, limit, PeriodType.QUARTER);
//    }

    /**
     * market-ticker로 연간 재무 테이블 조회
     */
    @Transactional
    public FinancialTableDto getAnnualTableByTicker(String market, String ticker, int limit) {
        log.info("=== getAnnualTableByTicker 호출 ===");
        log.info("market: {}, ticker: {}, limit: {}", market, ticker, limit);

        StockCode stockCode = stockCodeRepository.findByMarketAndTickerKrx(market, ticker)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("종목을 찾을 수 없습니다. (market=%s, ticker=%s)", market, ticker)));

        // Company 가져오기 또는 생성
        Company company = getOrCreateCompany(stockCode.getStockId());
        log.info("Company 조회/생성 완료: companyId={}", company.getCompanyId());

        // 재무 데이터 조회 (크롤링 포함)
        return getFinancialTable(company, limit, PeriodType.ANNUAL);
    }

    /**
     * market-ticker로 분기 재무 테이블 조회
     */
    @Transactional
    public FinancialTableDto getQuarterTableByTicker(String market, String ticker, int limit) {
        log.info("=== getQuarterTableByTicker 호출 ===");
        log.info("market: {}, ticker: {}, limit: {}", market, ticker, limit);

        StockCode stockCode = stockCodeRepository.findByMarketAndTickerKrx(market, ticker)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("종목을 찾을 수 없습니다. (market=%s, ticker=%s)", market, ticker)));

        // Company 가져오기 또는 생성
        Company company = getOrCreateCompany(stockCode.getStockId());
        log.info("Company 조회/생성 완료: companyId={}", company.getCompanyId());

        // 재무 데이터 조회 (크롤링 포함)
        return getFinancialTable(company, limit, PeriodType.QUARTER);
    }

    /**
     * Company 조회 또는 생성
     */
    @Transactional
    public Company getOrCreateCompany(Long stockId) {
        log.info("=== getOrCreateCompany 호출: stockId={} ===", stockId);

        return companyRepository.findByStockCode_StockId(stockId)
                .orElseGet(() -> {
                    StockCode stockCode = stockCodeRepository.findById(stockId)
                            .orElseThrow(() -> new CustomException(StockErrorCode.STOCK_NOT_FOUND));

                    Company company = Company.builder()
                            .stockCode(stockCode)
                            .currency("KRW")
                            .build();

                    Company saved = companyRepository.save(company);
                    log.info("새 Company 생성: companyId={}, ticker={}", saved.getCompanyId(), stockCode.getTickerKrx());
                    return saved;
                });
        // 크롤링 로직 분리
//        Optional<Company> existingCompany = companyRepository.findByStockCode_StockId(stockId);
//        if (existingCompany.isPresent()) {
//            log.info("기존 Company 발견: companyId={}", existingCompany.get().getCompanyId());
//            return existingCompany.get();
//        }
//
//        // StockCode 조회
//        StockCode stockCode = stockCodeRepository.findById(stockId)
//                .orElseThrow(() -> new IllegalArgumentException("StockCode를 찾을 수 없습니다. stockId: " + stockId));
//
//        log.info("새로운 Company 생성 중: ticker={}", stockCode.getTickerKrx());
//
//        // 2) 기본값만으로 Company 생성 (fetchMeta 사용 X)
//        Company company = Company.builder()
//                .stockCode(stockCode)
//                .currency("KRW")     // 우선 기본값
//                .build();
//
//
//        Company savedCompany = companyRepository.save(company);
//        log.info("Company 생성 완료: companyId={}", savedCompany.getCompanyId());
//
//
//        // 3) 주식 총수 현황 API 호출해서 shares_outstanding / stock_share_status 세팅
//        try {
//            String corpCode = stockCode.getDartCorpCode();   // 네 도메인에 맞게
//
//            // 예: 직전 사업연도 기준 최근 10년
//            int lastYear  = LocalDate.now().getYear() - 1;  // 2025년에 호출이면 2024
//            int firstYear = lastYear - 9;                   // 최근 10개 연도
//
//            for (int year = firstYear; year <= lastYear; year++) {
//                try {
//                    dartCrawlingService.fetchAndSaveShareStatus(
//                            savedCompany,
//                            corpCode,
//                            year
//                    );
//                } catch (Exception ex) {
//                    // 특정 연도 하나 실패해도 나머지 연도는 계속 시도
//                    log.warn("Company 생성은 성공했으나 {}년 주식총수 수집 실패 companyId={}, corpCode={}",
//                            year, savedCompany.getCompanyId(), corpCode, ex);
//                }
//            }
//        } catch (Exception e) {
//            log.warn("Company 생성은 성공했으나 주식총수 수집 실패 companyId={}",
//                    savedCompany.getCompanyId(), e);
//        }
//        return savedCompany;
    }

    /**
     * 연간, 분기 재무 테이블 조회 - DB 우선, 없으면 크롤링
     */
    @Transactional
    public FinancialTableDto getFinancialTable(Company company, int limit, PeriodType type) {
        log.info("=== getQuarterTable 호출 ===");
        log.info("companyId: {}, limit: {}", company.getCompanyId(), limit);

        Long companyId = company.getCompanyId();

        // 1) DB 조회
        List<FinPeriod> periods = switch (type) {
            case ANNUAL -> finPeriodRepository.findRecentYearlyPeriods(companyId, limit);
            case QUARTER -> finPeriodRepository.findRecentQuarterlyPeriods(companyId, limit);
        };

        log.info("DB 조회 결과: {} 개 기간", periods.size());


//        if (periods.isEmpty()) {
//            log.info("재무 데이터가 DB에 없음. 크롤링 시도");
//            StockCode stockCode = Optional.ofNullable(company.getStockCode())
//                    .orElseGet(() -> stockCodeRepository.findById(companyId)
//                            .orElseThrow(() -> new IllegalArgumentException("종목 코드 정보가 없습니다.")));
//
//
//            log.info("크롤링 시작: ticker={}", stockCode.getTickerKrx());
//
//            int saved = dartCrawlingService.crawlAndSaveFinancialData(companyId, stockCode.getTickerKrx());
//
//            if (saved > 0) {
//                periods = switch (type) {
//                    case ANNUAL  -> finPeriodRepository.findRecentYearlyPeriods(companyId, limit);
//                    case QUARTER -> finPeriodRepository.findRecentQuarterlyPeriods(companyId, limit);
//                };
//            }
//        }

        if (periods.isEmpty()) {
            return FinancialTableDto.builder()
                    .headers(Collections.emptyList())
                    .rows(Collections.emptyList())
                    .build();
        }
        return buildFinancialTable(companyId, periods);
    }

    /**
     * 재무 테이블 구축
     */
    private FinancialTableDto buildFinancialTable(Long companyId, List<FinPeriod> periods) {
        log.info("=== buildFinancialTable 호출 ===");
        log.info("companyId: {}, periods: {}", companyId, periods.size());

        // 1. 헤더 구성 (기간)
        List<FinancialTableDto.PeriodHeaderDto> headers = periods.stream()
                .map(period -> FinancialTableDto.PeriodHeaderDto.builder()
                        .periodId(period.getPeriodId())
                        .label(period.getLabel())
                        .fiscalYear(period.getFiscalYear())
                        .fiscalQuarter(period.getFiscalQuarter())
                        .isEstimate(period.getIsEstimate())
                        .build())
                .collect(Collectors.toList());

        // 2. 지표 정의 조회
        List<FinMetricDef> metricDefs = finMetricDefRepository.findAllByOrderByDisplayOrder();
        log.info("지표 정의 개수: {}", metricDefs.size());

        // 3. 기간 ID 목록
        List<Long> periodIds = periods.stream()
                .map(FinPeriod::getPeriodId)
                .collect(Collectors.toList());

        // 4. 모든 지표 값 조회
        List<FinMetricValue> allValues = finMetricValueRepository.findByCompanyIdAndPeriodIds(companyId, periodIds);
        log.info("지표 값 개수: {}", allValues.size());

        // 5. periodId + metricCode로 빠른 조회를 위한 맵 생성
        Map<String, BigDecimal> valueMap = allValues.stream()
                .collect(Collectors.toMap(
                        v -> v.getPeriodId() + "_" + v.getMetricCode(),
                        FinMetricValue::getValueNum,
                        (v1, v2) -> v1  // 중복 시 첫 번째 값 사용
                ));

        // 6. 행 구성 (지표별)
        List<FinancialTableDto.MetricRowDto> rows = metricDefs.stream()
                .map(metricDef -> {
                    Map<Long, BigDecimal> rowValues = new HashMap<>();

                    for (FinPeriod period : periods) {
                        String key = period.getPeriodId() + "_" + metricDef.getMetricCode();
                        BigDecimal value = valueMap.get(key);
                        if (value != null) {
                            rowValues.put(period.getPeriodId(), value);
                        }
                    }

                    return FinancialTableDto.MetricRowDto.builder()
                            .metricCode(metricDef.getMetricCode())
                            .metricName(metricDef.getNameKor())
                            .unit(metricDef.getUnit())
                            .values(rowValues)
                            .build();
                })
                .filter(row -> !row.getValues().isEmpty())  // 값이 없는 행은 제외
                .collect(Collectors.toList());

        log.info("테이블 구성 완료: headers={}, rows={}", headers.size(), rows.size());

        return FinancialTableDto.builder()
                .headers(headers)
                .rows(rows)
                .build();
    }

    /**
     * 특정 지표의 최근 값 조회
     */
    public BigDecimal getRecentMetricValue(Long companyId, String metricCode, String periodType, int nth) {
        List<FinPeriod> periods;

        if ("YEAR".equals(periodType)) {
            periods = finPeriodRepository.findRecentYearlyPeriods(companyId, nth);
        } else {
            periods = finPeriodRepository.findRecentQuarterlyPeriods(companyId, nth);
        }

        if (periods.isEmpty() || periods.size() < nth) {
            return null;
        }

        Long periodId = periods.get(nth - 1).getPeriodId();

        return finMetricValueRepository
                .findByCompanyIdAndPeriodIdAndMetricCode(companyId, periodId, metricCode)
                .map(FinMetricValue::getValueNum)
                .orElse(null);
    }
}
