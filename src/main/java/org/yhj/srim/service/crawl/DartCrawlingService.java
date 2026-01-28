package org.yhj.srim.service.crawl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.yhj.srim.repository.*;
import org.yhj.srim.repository.entity.*;
import org.yhj.srim.service.domain.BpsCalculatorService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * DART 전자공시 API를 사용한 재무정보 크롤링 서비스
 * 사업보고서의 재무제표 데이터를 수집하여 저장합니다.
 * (분기 데이터는 제외하고 연간 사업보고서만 수집)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DartCrawlingService {

    @Value("${dart.api.key:**************apikey}")
    private String dartApiKey;

    private final FinPeriodRepository finPeriodRepository;
    private final FinMetricDefRepository finMetricDefRepository;
    private final FinMetricValueRepository finMetricValueRepository;
    private final CompanyRepository companyRepository;
    private final StockCodeRepository stockCodeRepository;
    private final StockShareStatusRepository stockShareStatusRepository;
    private final BpsCalculatorService bpsCalculatorService;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://opendart.fss.or.kr/api")
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // DART 계정과목명 -> 내부 지표 코드 매핑
    private static final Map<String, String> ACCOUNT_TO_METRIC = new LinkedHashMap<>();
    
    static {
        // 재무상태표 (BS)
        ACCOUNT_TO_METRIC.put("자산총계", "TOTAL_ASSETS");
        ACCOUNT_TO_METRIC.put("유동자산", "CURRENT_ASSETS");
        ACCOUNT_TO_METRIC.put("비유동자산", "NON_CURRENT_ASSETS");
        ACCOUNT_TO_METRIC.put("부채총계", "TOTAL_LIABILITIES");
        ACCOUNT_TO_METRIC.put("유동부채", "CURRENT_LIABILITIES");
        ACCOUNT_TO_METRIC.put("비유동부채", "NON_CURRENT_LIABILITIES");
        ACCOUNT_TO_METRIC.put("자본총계", "TOTAL_EQUITY");
        
        // 손익계산서 (IS)
        ACCOUNT_TO_METRIC.put("매출액", "SALES");
        ACCOUNT_TO_METRIC.put("수익(매출액)", "SALES");
        ACCOUNT_TO_METRIC.put("영업이익", "OP_INC");
        ACCOUNT_TO_METRIC.put("영업이익(손실)", "OP_INC");
        ACCOUNT_TO_METRIC.put("당기순이익", "NET_INC");
        ACCOUNT_TO_METRIC.put("당기순이익(손실)", "NET_INC");
        ACCOUNT_TO_METRIC.put("법인세비용차감전순이익", "PRETAX_INC");
        ACCOUNT_TO_METRIC.put("법인세비용차감전순이익(손실)", "PRETAX_INC");
        ACCOUNT_TO_METRIC.put("영업이익률", "OPM");
        ACCOUNT_TO_METRIC.put("순이익률", "NET_MARGIN");
        
        // 현금흐름표 (CF)
        ACCOUNT_TO_METRIC.put("영업활동현금흐름", "CF_OPERATIONS");
        ACCOUNT_TO_METRIC.put("투자활동현금흐름", "CF_INVESTING");
        ACCOUNT_TO_METRIC.put("재무활동현금흐름", "CF_FINANCING");
        
        // 주당 지표
        ACCOUNT_TO_METRIC.put("기본주당이익", "EPS");
        ACCOUNT_TO_METRIC.put("주당순이익", "EPS");
    }

    /**
     * DART stockTotqySttus.json 을 호출해서
     * 특정 회사/연도에 대한 주식수 현황(합계)을 저장한다.
     *
     * @param company   Company 엔티티
     * @param corpCode  8자리 DART corp_code
     * @param bsnsYear  사업연도(예: 2024)
     * @return 저장 또는 업데이트된 엔티티
     */
    @Transactional
    public StockShareStatus fetchAndSaveShareStatus(Company company,
                                                    String corpCode,
                                                    int bsnsYear) {
        log.info("[SHARE] 주식총수 조회 시작 corpCode={}, year={}", corpCode, bsnsYear);

        // https://opendart.fss.or.kr/api/stockTotqySttus.json?crtfc_key=&corp_code=00113410&bsns_year=2024&reprt_code=11011
        String response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stockTotqySttus.json")
                        .queryParam("crtfc_key", dartApiKey)
                        .queryParam("corp_code", corpCode)
                        .queryParam("bsns_year", String.valueOf(bsnsYear))
                        .queryParam("reprt_code", "11011")   // 사업보고서
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        JsonNode root;

        try {
            root = objectMapper.readTree(response);
        } catch (Exception e) {
            log.error("[SHARE] JSON 파싱 실패 corpCode={}, year={}, response={}",
                    corpCode, bsnsYear, response, e);
            throw new IllegalStateException("DART 주식총수 JSON 파싱 실패", e);
        }

        String status = root.path("status").asText();
        if (!"000".equals(status)) {
            String msg = root.path("message").asText();
            log.warn("[SHARE] 조회 실패 corpCode={}, year={}, status={}, msg={}",
                    corpCode, bsnsYear, status, msg);
            throw new IllegalStateException("DART 주식총수 조회 실패: " + msg);
        }

        JsonNode list = root.path("list");
        if (!list.isArray() || list.isEmpty()) {
            log.warn("[SHARE] list 비어있음 corpCode={}, year={}", corpCode, bsnsYear);
            throw new IllegalStateException("DART 주식총수 list 비어있음");
        }

        // 🔸 여기서 se선택
        //  - 보통주만 저장하고 싶으면 targetSe = "보통주"
        //  - 전체 합계 사용하고 싶으면 targetSe = "합계"
        String targetSe = "보통주";

        JsonNode target = null;
        for (JsonNode node : list) {
            String se = node.path("se").asText();
            if (targetSe.equals(se)) {
                target = node;
                break;
            }
        }

        if (target == null) {
            // fallback: 보통주만 있을 수도 있으니 필요하면 보정 로직 추가 가능
            log.warn("[SHARE] se={} 항목을 찾지 못함 corpCode={}, year={}",
                    targetSe, corpCode, bsnsYear);
            throw new IllegalStateException("DART 주식총수에서 " + targetSe + " 행을 찾지 못함");
        }

        String se = target.path("se").asText();
        LocalDate stlmDt = LocalDate.parse(target.path("stlm_dt").asText());

        Long isu_stock_totqy = parseLongSafe(target.path("isu_stock_totqy").asText());
        Long istc_totqy      = parseLongSafe(target.path("istc_totqy").asText());
        Long tesstk_co       = parseLongSafe(target.path("tesstk_co").asText());
        Long distb_stock_co  = parseLongSafe(target.path("distb_stock_co").asText());

        // 기존 레코드 있으면 업데이트
        StockShareStatus statusEntity =
                stockShareStatusRepository
                        .findByCompany_CompanyIdAndBsnsYearAndSe(
                                company.getCompanyId(), bsnsYear, se)
                        .orElseGet(() -> StockShareStatus.builder()
                                .company(company)
                                .bsnsYear(bsnsYear)
                                .se(se)
                                .settlementDate(stlmDt)
                                .build());

        statusEntity = StockShareStatus.builder()
                .stockStatusId(statusEntity.getStockStatusId())
                .company(company)
                .bsnsYear(bsnsYear)
                .settlementDate(stlmDt)
                .se(se)
                .isuStockTotqy(isu_stock_totqy)
                .istcTotqy(istc_totqy)
                .tesstkCo(tesstk_co)
                .distbStockCo(distb_stock_co)
                .build();

        StockShareStatus saved = stockShareStatusRepository.save(statusEntity);

        // ▾ 여기서 company.shares_outstanding 업데이트 (유통주식 우선, 없으면 발행주식)
        Long shares = (distb_stock_co != null) ? distb_stock_co : istc_totqy;
        if (shares != null) {
            company.updateSharesOutstanding(shares);
            companyRepository.save(company);  // 명시적으로 저장
        }

        log.info("[SHARE] 저장 완료 companyId={}, year={}, se={}, shares={}",
                company.getCompanyId(), bsnsYear, se, shares);

        return saved;
    }

    private Long parseLongSafe(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        if (trimmed.isEmpty() || "-".equals(trimmed)) return null;
        trimmed = trimmed.replace(",", "");
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            log.warn("[SHARE-PARSE] 숫자 파싱 실패 value={}", s, e);
            return null;
        }
    }

    /**
     * 특정 회사의 DART 고유번호를 조회
     * @param companyName 회사명 (예: "삼성전자")
     * @return DART 고유번호 (8자리)
     */
    public String getCorpCode(String companyName) {
        try {
            // DART에서 제공하는 기업 고유번호 API는 파일 다운로드 형태이므로
            // 여기서는 직접 알고 있는 주요 기업 코드를 반환하거나
            // 별도로 다운로드한 코드 목록을 사용해야 합니다.
            
            // 주요 기업 코드 맵
            Map<String, String> knownCorpCodes = new HashMap<>();
            knownCorpCodes.put("삼성전자", "00126380");
            knownCorpCodes.put("SK하이닉스", "00164779");
            knownCorpCodes.put("NAVER", "00139670");
            knownCorpCodes.put("카카오", "00177269");
            knownCorpCodes.put("LG전자", "00148888");
            knownCorpCodes.put("현대자동차", "00164742");
            knownCorpCodes.put("기아", "00164779");
            
            return knownCorpCodes.get(companyName);
            
        } catch (Exception e) {
            log.error("DART 고유번호 조회 실패: {}", companyName, e);
        }
        return null;
    }

    /**
     * 재무정보 크롤링 및 저장 (사업보고서 기준, 최대한 많은 연도)
     * NaverCrawlingService의 crawlAndSaveFinancialData와 동일한 시그니처
     * 
     * @param companyId 회사 ID
     * @param tickerKrx KRX 티커 또는 DART 고유번호 (8자리면 DART 코드로 간주)
     * @return 저장된 데이터 건수
     */
    @Transactional
    public int crawlAndSaveFinancialData(Long companyId, String tickerKrx) {
        try {
            Company company = companyRepository.findById(companyId)
                    .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyId));

            String dartCorpCode = company.getStockCode().getDartCorpCode();
            if (dartCorpCode == null || dartCorpCode.length() != 8) {
                log.warn("유효하지 않은 DART 코드: {}. 기업명으로 조회 시도", dartCorpCode);
                return 0;
            }

            int currentYear = LocalDate.now().getYear();
            int startYear = 2015;
            int savedCount = 0;

            log.info("DART 재무정보 크롤링 시작 - companyId: {}, ticker={}, corpCode={}",
                    companyId, tickerKrx, dartCorpCode);

            // 먼저 가장 최근 연도의 주식수를 가져와서 Company 테이블에 저장
            boolean sharesSaved = false;
            for (int year = currentYear; year >= startYear && !sharesSaved; year--) {
                try {
                    log.info("{}년 주식총수 조회 중...", year);
                    StockShareStatus shareStatus = fetchAndSaveShareStatus(company, dartCorpCode, year);
                    log.info("{}년 주식총수 저장 완료 - shares={}", year, company.getSharesOutstanding());
                    sharesSaved = true;
                    
                    // API 호출 제한 방지
                    Thread.sleep(1000);
                } catch (Exception e) {
                    log.warn("{}년 주식총수 조회 실패: {}", year, e.getMessage());
                }
            }

            if (!sharesSaved) {
                log.warn("모든 연도의 주식총수 조회 실패");
            }

            // 연도별 재무 데이터 저장
            for (int year = currentYear; year >= startYear; year--) {
                try {
                    int yearSaved = crawlAndSaveFinancialForYear(company, dartCorpCode, year);
                    savedCount += yearSaved;
                    log.info("{}년 재무 데이터 저장 완료 - {} 건", year, yearSaved);
                } catch (Exception e) {
                    log.warn("{}년 재무 데이터 처리 실패: {}", year, e.getMessage());
                }
            }


            // 여기서 BPS 전 기간 재계산
            int bpsUpdated = bpsCalculatorService.recalcAllBpsForCompany(companyId);
            log.info("BPS 재계산 완료 - companyId={}, updated={} rows", companyId, bpsUpdated);


            log.info("DART 재무정보 크롤링 완료 - 총 {} 건 저장", savedCount);
            return savedCount;

        } catch (Exception e) {
            log.error("DART 재무정보 크롤링 실패", e);
            throw new RuntimeException("DART 재무정보 크롤링 실패: " + e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int crawlAndSaveFinancialForYear(Company company, String dartCorpCode, int year) throws InterruptedException {
        Long companyId = company.getCompanyId();

        log.info("{}년 사업보고서 조회 중... (companyId={}, corpCode={})", year, companyId, dartCorpCode);

        Map<String, BigDecimal> financialData = fetchFinancialDataForYear(dartCorpCode, year);

        // 여기서 한 번 전체 덤프
        log.info("=== {}년 DART 재무 데이터 ({}개 지표) ===", year, financialData.size());
        financialData.forEach((key, value) ->
                log.info("   • key='{}', value={}", key, value)
        );

        if (financialData.isEmpty()) {
            log.warn("{}년 재무 데이터 없음", year);
            return 0;
        }

        // 이 트랜잭션 안에서만 fin_period / fin_metric_value 저장
        FinPeriod period = saveOrUpdatePeriod(companyId, year, 12, false);

        int yearSaved = 0;
        for (Map.Entry<String, BigDecimal> entry : financialData.entrySet()) {
            String metricCode = entry.getKey();
            BigDecimal value  = entry.getValue();
            saveOrUpdateMetricValue(companyId, period, metricCode, value);
            yearSaved++;
        }

        log.info("{}년 재무 데이터 저장 완료 - {} 건", year, yearSaved);

        // API 호출 제한 방지
        Thread.sleep(1000);

        return yearSaved;
    }


    /**
     * 특정 연도의 사업보고서 재무제표 데이터 조회
     */
    private Map<String, BigDecimal> fetchFinancialDataForYear(String corpCode, int year) {
        // 1) DART에서 가져온 계정과목들을 내부 코드로 모아두는 raw map
        Map<String, BigDecimal> raw = new LinkedHashMap<>();
        Map<String, BigDecimal> prevRaw = new LinkedHashMap<>();    // 전기 값들 (ROE용)
        Map<String, BigDecimal> result = new LinkedHashMap<>();

        try {
            String response = webClient.get()
                    // https://opendart.fss.or.kr/api/fnlttSinglAcntAll.json?crtfc_key=&corp_code=00113410&bsns_year=2024&reprt_code=11011&fs_div=CFS
                    .uri(uriBuilder -> uriBuilder
                            .path("/fnlttSinglAcntAll.json")
                            .queryParam("crtfc_key", dartApiKey)
                            .queryParam("corp_code", corpCode)
                            .queryParam("bsns_year", String.valueOf(year))
                            .queryParam("reprt_code", "11011") // 사업보고서
                            .queryParam("fs_div", "CFS")       // 연결재무제표
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            String status = root.path("status").asText();

            if (!"000".equals(status)) {
                log.debug("{}년 재무제표 조회 실패: {}", year, root.path("message").asText());
                return result;
            }

            JsonNode list = root.path("list");
            for (JsonNode item : list) {
                String sjDiv        = item.path("sj_div").asText();      // ✅ 추가
                String accountId   = item.path("account_id").asText(); // 추가로 씀
                String accountNm   = item.path("account_nm").asText();
                String thstrmAmount = item.path("thstrm_amount").asText();
                String frmtrmAmount = item.path("frmtrm_amount").asText();   // 전기 추가

                String metricCode = mapAccountToMetric(sjDiv, accountId, accountNm);
                if (metricCode == null) {
                    // 디버깅용 로그 찍고 스킵
                    log.debug("[FS-MAP][UNMAPPED] year={}, sjDiv={}, accountId={}, accountNm={}",
                            year, sjDiv, accountId, accountNm);
                    continue;
                }

                BigDecimal currVal = parseAmount(thstrmAmount);
                BigDecimal prevVal = parseAmount(frmtrmAmount);

                if (currVal != null) {
                    // NET_INC는 한 번 들어간 값(ProfitLoss)을 우선으로 유지
                    if ("NET_INC".equals(metricCode) && raw.containsKey("NET_INC")) {
                        log.debug("[FS-MAP][DUP] NET_INC 이미 존재: old={}, new={}, accountId={}, accountNm={}",
                                raw.get("NET_INC"), currVal, accountId, accountNm);
                        // 덮어쓰지 않고 스킵
                    } else if ("NET_INC_OWNER".equals(metricCode) && raw.containsKey("NET_INC_OWNER")) {
                        log.debug("[FS-MAP][DUP] NET_INC_OWNER 이미 존재: old={}, new={}, accountId={}, accountNm={}",
                                raw.get("NET_INC_OWNER"), currVal, accountId, accountNm);
                    } else {
                        raw.put(metricCode, currVal);
                    }
                }

                if (prevVal != null) {
                    prevRaw.put(metricCode, prevVal);
                }
            }

            log.info("=== {}년 DART 재무 데이터 RAW ({}개 지표) ===", year, raw.size());
            raw.forEach((k, v) -> log.info("   • raw[{}] = {}", k, v));

            if (!raw.containsKey("NET_INC")) {
                BigDecimal cont = raw.getOrDefault("CONT_NET_INC", BigDecimal.ZERO);
                BigDecimal disc = raw.getOrDefault("DISC_NET_INC", BigDecimal.ZERO);

                if (cont.compareTo(BigDecimal.ZERO) != 0 ||
                        disc.compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal netIncCalc = cont.add(disc);
                    raw.put("NET_INC", netIncCalc);
                    log.info(">>> 조립된 NET_INC (당기순이익) = {}", netIncCalc);
                }
            }
            // 2) raw 값을 이용해서 DB 기준 metric_code들 계산/매핑

            BigDecimal sales             = raw.get("SALES");
            BigDecimal opInc             = raw.get("OP_INC");
            BigDecimal netInc            = raw.get("NET_INC");          // 전체 당기순이익
            BigDecimal netIncOwner       = raw.get("NET_INC_OWNER");    // 지배주주 당기순이익
            BigDecimal totalAssets       = raw.get("TOTAL_ASSETS");
            BigDecimal totalLiab         = raw.get("TOTAL_LIABILITIES");
            BigDecimal equityTotalCurr   = raw.get("TOTAL_EQUITY");         // 전체 자본
            BigDecimal equityTotalPrev   = prevRaw.get("TOTAL_EQUITY");
            BigDecimal equityOwnerCurr   = raw.get("TOTAL_EQUITY_OWNER");   // 지배 기준 자본
            BigDecimal equityOwnerPrev   = prevRaw.get("TOTAL_EQUITY_OWNER");
            BigDecimal currentAssets     = raw.get("CURRENT_ASSETS");
            BigDecimal currentLiab       = raw.get("CURRENT_LIABILITIES");
            BigDecimal eps               = raw.get("EPS");
            BigDecimal bps               = raw.get("BPS"); // 나중에 DART에서 주당순자산 매핑하면 사용

            // (1) 기본 재무지표: SALES / OP_INC / NET_INC / EPS / BPS
            // ---------- 3단계: 기본 재무지표 결과 맵에 저장 ----------
            putIfNotNull(result, "SALES",        sales);
            putIfNotNull(result, "OP_INC",       opInc);
            putIfNotNull(result, "NET_INC",      netInc);
            putIfNotNull(result, "TOTAL_EQUITY", equityTotalCurr);
            putIfNotNull(result, "TOTAL_EQUITY_OWNER", equityOwnerCurr);
            putIfNotNull(result, "EPS",          eps);
            putIfNotNull(result, "BPS",          bps);

            // (2) 비율 지표 계산

            // (1) 영업이익률 OPM = 영업이익 / 매출 * 100
            BigDecimal opm = raw.get("OPM");
            if (opm == null) {
                opm = toPercent(safeDivide(opInc, sales));
            }
            putIfNotNull(result, "OPM", opm);

            // (2) 순이익률 NET_MARGIN = 당기순이익 / 매출 * 100
            BigDecimal netMargin = raw.get("NET_MARGIN");
            if (netMargin == null) {
                netMargin = toPercent(safeDivide(netInc, sales));
            }
            putIfNotNull(result, "NET_MARGIN", netMargin);

            // (3) 부채비율 DEBT_RATIO = 부채총계 / 자본총계(전체) * 100
            BigDecimal equityForDebt = (equityTotalCurr != null ? equityTotalCurr : equityOwnerCurr);
            BigDecimal debtRatio = toPercent(safeDivide(totalLiab, equityForDebt));
            putIfNotNull(result, "DEBT_RATIO", debtRatio);

            // (4) ROE (네이버 방식) = 지배주주 당기순이익 / 평균 지배주주자본 * 100
            BigDecimal roeSourceNetInc  = (netIncOwner != null ? netIncOwner : netInc); // 지배 없으면 전체로 fallback
            BigDecimal roeEquityCurr    = (equityOwnerCurr != null ? equityOwnerCurr : equityTotalCurr);
            BigDecimal roeEquityPrev    = (equityOwnerPrev != null ? equityOwnerPrev : equityTotalPrev);

            // ROE = 당기순이익 / 평균 자기자본 * 100
            if (roeSourceNetInc != null && roeEquityCurr != null && roeEquityPrev != null) {
                BigDecimal avgEquity = roeEquityCurr.add(roeEquityPrev)
                        .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);

                if (avgEquity.compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal roe = toPercent(roeSourceNetInc.divide(avgEquity, 8, RoundingMode.HALF_UP));
                    putIfNotNull(result, "ROE", roe);

                    log.debug("[ROE] year={} / netInc(used)={} / equity_curr={} / equity_prev={} / avgEquity={} / ROE={}",
                            year, roeSourceNetInc, roeEquityCurr, roeEquityPrev, avgEquity, roe);
                } else {
                    log.debug("[FS-METRIC][ROE] 평균 자기자본이 0이어서 계산 불가: year={}", year);
                }
            } else {
                log.debug("[FS-METRIC][ROE] netIncOwner/equityOwnerCurr/equityOwnerPrev 중 null 존재: year={}", year);
            }

            // ROA = 당기순이익 / 자산총계 * 100
            BigDecimal roa = toPercent(safeDivide(netInc, totalAssets));
            putIfNotNull(result, "ROA", roa);

            // QUICK_RATIO = 유동자산 / 유동부채 * 100 (단순 유동비율 정의 사용)
            BigDecimal quickRatio = toPercent(safeDivide(currentAssets, currentLiab));
            putIfNotNull(result, "QUICK_RATIO", quickRatio);

            // (3) 배당/주가 관련 지표 (DPS / DIVIDEND_YIELD / PER / PBR / PAYOUT_RATIO / RETENTION_RATIO)
            //  -> DART fnlttSinglAcntAll 만으로는 계산이 어려움 (주가/배당 정보 필요)
            //  -> 이 값들은 나중에 KRX/네이버/FnGuide 크롤러에서 별도로 채우거나,
            //     다른 서비스에서 계산해서 fin_metric_value에 넣는 게 자연스러움.
            //
            // 예: DPS, DIVIDEND_YIELD, PAYOUT_RATIO, RETENTION_RATIO, PER, PBR 등
            // -> 이 메서드는 "DART 기반 재무제표" 역할만 담당하고,
            //    나머지 지표는 다른 소스/배치에서 담당하도록 분리하는 게 깔끔.

            log.info("=== {}년 DART 기반 FIN_METRIC 결과 ({}개 지표) ===", year, result.size());
            result.forEach((k, v) -> log.info("   • metricCode='{}', value={}", k, v));

        } catch (Exception e) {
            log.error("{}년 재무제표 조회 실패", year, e);
        }

        return result;
    }
    private String normalizeAccountName(String name) {
        if (name == null) return "";
        String n = name;

        // 공백/괄호/언더스코어/대시 제거
        n = n.replace(" ", "")
                .replace("(", "")
                .replace(")", "")
                .replace("_", "")
                .replace("-", "")
                .replace(",", "");

        // 한글/영문 섞여도 통일
        n = n.toLowerCase();

        return n;
    }

    /**
     * DART 계정(account_id/account_nm, sj_div)을 내부 metric_code로 매핑한다.
     *
     * - BS(재무상태표) : TOTAL_ASSETS / TOTAL_LIABILITIES / TOTAL_EQUITY / TOTAL_EQUITY_OWNER
     * - CIS/IS(손익/포괄손익) : SALES / OP_INC / NET_INC / NET_INC_OWNER / CONT_NET_INC / DISC_NET_INC
     */
    private String mapAccountToMetric(String sjDiv, String accountId, String accountNm) {
//        String id   = accountId != null ? accountId.trim() : "";
//        String name = accountNm != null ? accountNm.trim() : "";
//        String norm = normalizeAccountName(name);  // 예: "관계기업의자본변동" → 공백/특수문자 제거 등
//        String sj   = sjDiv != null ? sjDiv.trim().toUpperCase() : "";
//
//        // ============= 1) ID 기반 우선 매핑 =============
//
//        // 1-1) 재무상태표(BS): 자산/부채/자본
//        if ("BS".equals(sj)) {
//            switch (id) {
//                // 자산총계
//                case "ifrs-full_Assets":
//                case "ifrs_Assets":
//                    return "TOTAL_ASSETS";
//
//                // 부채총계
//                case "ifrs-full_Liabilities":
//                case "ifrs_Liabilities":
//                    return "TOTAL_LIABILITIES";
//
//                // 자본총계 (전체 Equity: 지배 + 비지배)
//                case "ifrs-full_Equity":
//                case "ifrs_Equity":
//                    return "TOTAL_EQUITY";
//
//                // 자본총계(지배주주지분)
//                case "ifrs-full_EquityAttributableToOwnersOfParent":
//                    return "TOTAL_EQUITY_OWNER";
//            }
//        }
//
//        // 1-2) 손익계산서/포괄손익계산서(CIS/IS): 매출/이익 계열
//        if ("CIS".equals(sj) || "IS".equals(sj)) {
//            switch (id) {
//                // 매출
//                case "ifrs-full_Revenue":
//                case "ifrs_Revenue":
//                case "ifrs-full_SalesRevenue":
//                    return "SALES";
//
//                // 영업이익
//                case "ifrs-full_OperatingIncomeLoss":
//                case "dart_OperatingIncomeLoss":
//                    return "OP_INC";
//
//                // 전체 당기순이익 (지배+비지배)
//                case "ifrs-full_ProfitLoss":
//                case "ifrs_ProfitLoss":
//                    return "NET_INC";
//
//                // 지배주주 귀속 당기순이익
//                case "ifrs-full_ProfitLossAttributableToOwnersOfParent":
//                    return "NET_INC_OWNER";
//
//                // 계속/중단 영업 당기순이익
//                case "ifrs-full_ProfitLossFromContinuingOperations":
//                    return "CONT_NET_INC";
//
//                case "ifrs-full_ProfitLossFromDiscontinuedOperations":
//                case "ifrs-full_IncomeFromDiscontinuedOperationsAttributableToOwnersOfParent":
//                    return "DISC_NET_INC";
//            }
//        }
//
//        // ============= 2) 이름 기반 보정 매핑 =============
//
//        // 2-1) BS: 자산/부채/자본 이름 기반 (ID가 없거나 특이 케이스용)
//        if ("BS".equals(sj)) {
//            // 자산총계 → TOTAL_ASSETS
//            if (norm.contains("자산총계") || norm.equals("자산")) {
//                return "TOTAL_ASSETS";
//            }
//
//            // 부채총계 → TOTAL_LIABILITIES
//            if (norm.contains("부채총계") || norm.equals("부채")) {
//                return "TOTAL_LIABILITIES";
//            }
//
//            // 자본총계(지배) / 지배기업 소유주 지분 → TOTAL_EQUITY_OWNER
//            if (name.equals("자본총계(지배)")
//                    || norm.contains("지배기업소유주지분")
//                    || norm.contains("지배기업소유주지분합계")) {
//                return "TOTAL_EQUITY_OWNER";
//            }
//
//            // 자본총계(전체) → TOTAL_EQUITY
//            if (name.equals("자본총계") || norm.equals("자본총계")) {
//                return "TOTAL_EQUITY";
//            }
//        }
//
//        // 2-2) CIS/IS: 매출/영업이익/당기순이익 이름 기반
//        if ("CIS".equals(sj) || "IS".equals(sj)) {
//
//            // --- 매출(매출액/영업수익) → SALES ---
//            if (!norm.contains("채권")   // 매출채권
//                    && !norm.contains("채무")
//                    && !norm.contains("원가")  // 매출원가
//                    && !norm.contains("총이익")) { // 매출총이익
//                if (norm.contains("매출") || norm.contains("영업수익") || norm.contains("revenue")) {
//                    return "SALES";
//                }
//            }
//
//            // --- 영업이익 → OP_INC ---
//            if (!norm.contains("계속영업") && !norm.contains("중단영업")) {
//                if (norm.equals("영업이익") ||
//                        norm.equals("영업손실") ||
//                        norm.equals("영업이익손실") ||
//                        norm.equals("영업이익및손실")) {
//                    return "OP_INC";
//                }
//            }
//
//            // --- 당기순이익(전체) → NET_INC ---
//            if (!norm.contains("귀속") && !norm.contains("지배기업") && !norm.contains("비지배지분")) {
//                if (norm.contains("당기순이익") ||
//                        norm.contains("당기순손익") ||
//                        norm.equals("순이익")       ||
//                        norm.equals("순손실")) {
//                    return "NET_INC";
//                }
//            }
//
//            // --- 당기순이익(지배) → NET_INC_OWNER ---
//            if (norm.contains("당기순이익지배")
//                    || norm.equals("당기순이익지배")
//                    || name.equals("당기순이익(지배)")) {
//                return "NET_INC_OWNER";
//            }
//
//            // ⚠ 여기엔 TOTAL_EQUITY / TOTAL_EQUITY_OWNER 매핑 넣지 말기
//            //   (CIS의 "관계기업의 자본변동" 같은 것을 막기 위해)
//        }
//
//        // ============= 3) 나머지는 아직 매핑 안 함 =============
        return null;
    }

    private void putIfNotNull(Map<String, BigDecimal> map, String key, BigDecimal value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private BigDecimal safeDivide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || BigDecimal.ZERO.compareTo(denominator) == 0) {
            return null;
        }
        // 적당한 scale과 RoundingMode는 원하는 대로 조정
        return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal toPercent(BigDecimal ratio) {
        if (ratio == null) return null;
        return ratio.multiply(BigDecimal.valueOf(100));
    }
    /**
     * 금액 문자열을 BigDecimal로 변환
     */
    private BigDecimal parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isEmpty() || amountStr.equals("-")) {
            return null;
        }

        try {
            // 쉼표 제거 후 변환
            String cleaned = amountStr.replace(",", "").trim();
            if (cleaned.isEmpty()) {
                return null;
            }
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            log.debug("금액 변환 실패: {}", amountStr);
            return null;
        }
    }

    /**
     * 기간 정보 저장 또는 업데이트
     */
    private FinPeriod saveOrUpdatePeriod(Long companyId, int fiscalYear, int fiscalMonth, boolean isQuarter) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyId));

        String periodType = "YEAR"; // 사업보고서는 연간 데이터
        Integer fiscalQuarter = null;

        Optional<FinPeriod> existing = finPeriodRepository
                .findByCompany_CompanyIdAndPeriodTypeAndFiscalYearAndFiscalQuarter(
                        companyId, periodType, fiscalYear, fiscalQuarter);

        if (existing.isPresent()) {
            log.debug("기존 기간 사용: {}년", fiscalYear);
            return existing.get();
        }

        FinPeriod period = FinPeriod.builder()
                .company(company)
                .periodType(periodType)
                .fiscalYear(fiscalYear)
                .fiscalQuarter(fiscalQuarter)
                .periodStart(LocalDate.of(fiscalYear, 1, 1))
                .periodEnd(LocalDate.of(fiscalYear, 12, 31))
                .label(fiscalYear + ".12") // YYYY.12 형식으로 통일
                .isEstimate(false)
                .build();

        FinPeriod saved = finPeriodRepository.save(period);
        log.debug("새 기간 저장: {}년", fiscalYear);
        return saved;
    }

    /**
     * 지표 값 저장 또는 업데이트
     */
    private void saveOrUpdateMetricValue(Long companyId, FinPeriod period, String metricCode, BigDecimal value) {
        log.debug("지표 값 저장 - company={}, period={}, metric={}, value={}",
                companyId, period, metricCode, value);

        Optional<FinMetricValue> existing = finMetricValueRepository
                .findByCompanyIdAndPeriodAndMetricCode(companyId, period, metricCode);

        if (existing.isPresent()) {
            FinMetricValue metricValue = existing.get();
            metricValue.setValueNum(value);
            metricValue.setSource("DART");
            finMetricValueRepository.save(metricValue);
            log.debug("지표 값 업데이트: {} = {}", metricCode, value);
        } else {
            FinMetricValue metricValue = FinMetricValue.builder()
                    .companyId(companyId)
                    .period(period)
                    .metricCode(metricCode)
                    .valueNum(value)
                    .source("DART")
                    .build();
            finMetricValueRepository.save(metricValue);
            log.debug("지표 값 저장: {} = {}", metricCode, value);
        }
    }
}
