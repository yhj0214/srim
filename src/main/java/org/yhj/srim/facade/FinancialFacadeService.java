package org.yhj.srim.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.client.DartClient;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.StockErrorCode;
import org.yhj.srim.repository.FinMetricDefRepository;
import org.yhj.srim.repository.FinMetricValueRepository;
import org.yhj.srim.repository.FinPeriodRepository;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.FinMetricDef;
import org.yhj.srim.repository.entity.FinMetricValue;
import org.yhj.srim.repository.entity.FinPeriod;
import org.yhj.srim.service.CrawlingService;
import org.yhj.srim.service.FinancialService;
import org.yhj.srim.service.dto.FinancialTableDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialFacadeService {

    private final FinancialService financialService;
    private final CrawlingService crawlingService;
    private final FinPeriodRepository finPeriodRepository;
    private final FinMetricDefRepository finMetricDefRepository;
    private final FinMetricValueRepository finMetricValueRepository;

    /**
     * 1. company 조회, 없을 시 생성
     * 2. 재무제표, 주식 수 크롤링 및 저장
     * 3. 저장된 값들로 지표 계산 및 financialTableDto생성
     */
    @Transactional
    public FinancialTableDto getAnnualTable(Long stockId, int limit) {
        Optional<Company> existingOpt = financialService.findCompanyByStockId(stockId);

        Company company;
        boolean isNewCompany = false;

        if (existingOpt.isPresent()) {
            // 기존 회사
            company = existingOpt.get();
            log.info("기존 Company, 저장된 데이터로 조회만 실행 - companyId={}", company.getCompanyId());
        } else {
            // 신규 회사
            company = financialService.getOrCreateCompany(stockId);
            isNewCompany = true;
            log.info("신규 Company, 전체 크롤링 및 저장 실행 - companyId={}", company.getCompanyId());
        }

        // 신규 회사인 경우 전체 파이프라인 실행
        if (isNewCompany) {
            runFullPipeline(company, limit);
        }

        // 저장된 값으로 DTO 생성
        return buildAnnualTableDto(company, limit);
    }

    private FinancialTableDto buildAnnualTableDto(Company company, int limit) {
        Long companyId = company.getCompanyId();
        int currentYear = LocalDate.now().getYear();
        int startYear = currentYear - limit + 1;

        for(int year = currentYear; year >= startYear; year--){
            financialService.getOrBuildAnnualMetrics(companyId, year);
        }

        List<FinPeriod> periods = finPeriodRepository
                .findByCompany_CompanyIdAndPeriodTypeAndFiscalYearBetweenAndIsEstimateOrderByFiscalYearDesc(
                        companyId, "YEAR", startYear, currentYear, false
                );

        if (periods.isEmpty()) {
            log.warn("FinPeriod 없음 - companyId={}, years={}~{}", companyId, startYear, currentYear);
            return new FinancialTableDto(List.of(), List.of());
        }

        List<FinancialTableDto.PeriodHeaderDto> headers = periods.stream()
                .map(p -> FinancialTableDto.PeriodHeaderDto.builder()
                        .periodId(p.getPeriodId())
                        .label(p.getLabel())              // ex) "2024/12"
                        .fiscalYear(p.getFiscalYear())
                        .fiscalQuarter(p.getFiscalQuarter())
                        .isEstimate(p.getIsEstimate())
                        .build())
                .collect(Collectors.toList());

        // periodId 리스트 추출
        List<Long> periodIds = periods.stream()
                .map(FinPeriod::getPeriodId)
                .collect(Collectors.toList());

        // 해당 기간들에 대한 fin_metric_value 조회
        List<FinMetricValue> metricValues =
                finMetricValueRepository.findByCompanyIdAndPeriodIdIn(companyId, periodIds);

        // metricCode -> (periodId -> value) 맵 구성
        Map<String, Map<Long, BigDecimal>> metricCodeToPeriodValueMap = new HashMap<>();

        for (FinMetricValue v : metricValues) {
            String metricCode = v.getMetricCode();
            Long periodId = v.getPeriodId();
            BigDecimal value = v.getValueNum();

            metricCodeToPeriodValueMap
                    .computeIfAbsent(metricCode, k -> new HashMap<>())
                    .put(periodId, value);
        }

        // fin_metric_def 기준으로 행 구성
        List<FinMetricDef> metricDefs = finMetricDefRepository.findAllByOrderByDisplayOrderAsc();

        List<FinancialTableDto.MetricRowDto> rows = new ArrayList<>();

        for (FinMetricDef def : metricDefs) {
            String metricCode = def.getMetricCode();
            String nameKor = def.getNameKor();
            String unit = def.getUnit();

            Map<Long, BigDecimal> periodValueMap =
                    metricCodeToPeriodValueMap.getOrDefault(metricCode, Collections.emptyMap());

            // DTO에서는 수정 가능하도록 새 HashMap으로 복사
            Map<Long, BigDecimal> valueCopy = new LinkedHashMap<>();
            for (Long periodId : periodIds) {
                // 값이 없는 기간은 null 또는 아예 넣지 않을 수 있음
                // 여기서는 있는 값만 넣고, 프론트에서 없는 키는 공백 처리하게
                if (periodValueMap.containsKey(periodId)) {
                    valueCopy.put(periodId, periodValueMap.get(periodId));
                }
            }

            FinancialTableDto.MetricRowDto row = FinancialTableDto.MetricRowDto.builder()
                    .metricCode(metricCode)
                    .metricName(nameKor)
                    .unit(unit)
                    .values(valueCopy)
                    .build();

            rows.add(row);
        }
        return FinancialTableDto.builder()
                .headers(headers)
                .rows(rows)
                .build();
    }


    private void runFullPipeline(Company company, int limit) {
        String corpCode = company.getStockCode().getDartCorpCode();
        if (corpCode == null || corpCode.length() != 8) {
            throw new CustomException(StockErrorCode.DART_CODE_NOT_FOUND);
        }

        Long companyId = company.getCompanyId();
        int currentYear = LocalDate.now().getYear();
        int startYear   = currentYear - limit + 1;

        log.info("전체 파이프라인 실행 - companyId={}, corpCode={}, year {}~{}",
                companyId, corpCode, startYear, currentYear);

        for (int year = currentYear-1; year >= startYear; year--) {
            log.debug("{}년 크롤링 및 계산 진행", year);

            // 재무제표 크롤링 , dart_fs_filing + dart_fs_line DB저장
            crawlingService.crawlAndSaveAnnualFinancial(corpCode, companyId, year);

            // 주식수 크롤링 + dart_share_status 저장
            crawlingService.crawlAndSaveShareStatus(corpCode, companyId, year);

            // dart_fs_line 기반 -> fin_metric_value 저장
//            financialService.recalcAndSaveFinancialForYearFromDb(company, year);
        }
    }
    /**
     * dart_fs_line 기반 계산 결과로 연간 테이블 DTO 생성
     */
    private FinancialTableDto loadMetricsAsDto(Company company, int limit) {

        Long companyId = company.getCompanyId();
        int currentYear = LocalDate.now().getYear();
        int startYear   = currentYear - limit + 1;

        Map<Integer, Map<String, BigDecimal>> metricsByYear = new LinkedHashMap<>();

        for (int year = currentYear; year >= startYear; year--) {
            Map<String, BigDecimal> metrics =
                    financialService.buildFinancialMetrics(companyId, year);

            metricsByYear.put(year, metrics);
        }

        return buildFinancialTableDtoFromMetrics(metricsByYear);
    }


    private FinancialTableDto buildFinancialTableDtoFromMetrics(
            Map<Integer, Map<String, BigDecimal>> metricsByYear) {

        // 기간 헤더 생성 (연간만 다루니까 fiscalQuarter=null, isEstimate=false)
        List<FinancialTableDto.PeriodHeaderDto> headers = new ArrayList<>();

        // metricCode 별로 row 모으기
        Map<String, FinancialTableDto.MetricRowDto> rowMap = new LinkedHashMap<>();

        for (Map.Entry<Integer, Map<String, BigDecimal>> entry : metricsByYear.entrySet()) {
            Integer year = entry.getKey();
            Map<String, BigDecimal> metrics = entry.getValue();

            // 여기서는 편의상 periodId = year 로 사용 (나중에 FinPeriod 쓰면 교체)
            Long periodId = year.longValue();

            // label 은 "YYYY/12"
            String label = year + "/12";

            headers.add(FinancialTableDto.PeriodHeaderDto.builder()
                    .periodId(periodId)
                    .label(label)
                    .fiscalYear(year)
                    .fiscalQuarter(null)     // 연간
                    .isEstimate(false)
                    .build());

            // metric 들을 MetricRowDto 에 채워넣기
            for (Map.Entry<String, BigDecimal> mEntry : metrics.entrySet()) {
                String metricCode = mEntry.getKey();
                BigDecimal value  = mEntry.getValue();

                FinancialTableDto.MetricRowDto row =
                        rowMap.computeIfAbsent(metricCode, code -> FinancialTableDto.MetricRowDto.builder()
                                .metricCode(code)
                                .metricName(resolveMetricName(code))  // 한글명
                                .unit(resolveMetricUnit(code))        // 단위
                                .values(new LinkedHashMap<>())
                                .build()
                        );

                row.getValues().put(periodId, value);
            }
        }

        return FinancialTableDto.builder()
                .headers(headers)
                .rows(new ArrayList<>(rowMap.values()))
                .build();
    }

    private String resolveMetricName(String code) {
        return switch (code) {
            case "SALES"              -> "매출액";
            case "OP_INC"             -> "영업이익";
            case "NET_INC"            -> "당기순이익";
            case "NET_INC_OWNER"      -> "지배주주 순이익";
            case "TOTAL_EQUITY"       -> "자본총계";
            case "TOTAL_EQUITY_OWNER" -> "지배주주지분";
            case "ROE"                -> "ROE";
            case "ROA"                -> "ROA";
            case "OPM"                -> "영업이익률";
            case "NET_MARGIN"         -> "순이익률";
            case "DEBT_RATIO"         -> "부채비율";
            case "QUICK_RATIO"        -> "유동비율";
            case "EPS"                -> "EPS";
            case "BPS"                -> "BPS";
            default                   -> code; // 기본은 코드 그대로
        };
    }

    private String resolveMetricUnit(String code) {
        // 단위는 너 설계에 맞게
        return switch (code) {
            case "ROE", "ROA", "OPM", "NET_MARGIN", "DEBT_RATIO", "QUICK_RATIO" -> "%";
            case "EPS", "BPS" -> "원/주";
            default -> "백만원";
        };
    }
}
