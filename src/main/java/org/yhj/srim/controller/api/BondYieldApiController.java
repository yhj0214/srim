package org.yhj.srim.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.yhj.srim.controller.dto.ApiResponse;
import org.yhj.srim.repository.entity.BondYieldCurve;
import org.yhj.srim.service.BondYieldCrawlingService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bond-yields")
@RequiredArgsConstructor
@Slf4j
public class BondYieldApiController {

    private final BondYieldCrawlingService bondYieldCrawlingService;

    /**
     * 회사채 수익률 크롤링 및 업데이트
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refreshBondYields() {
        try {
            log.info("회사채 수익률 새로고침 요청");
            
            // 크롤링 실행
            bondYieldCrawlingService.crawlAndSaveBondYields();
            
            // 최신 데이터 조회
            List<BondYieldCurve> latestData = bondYieldCrawlingService.getLatestBondYields();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "회사채 수익률이 업데이트되었습니다.");
            result.put("date", LocalDate.now().toString());
            result.put("count", latestData.size());
            
            return ResponseEntity.ok(ApiResponse.success(result));
            
        } catch (Exception e) {
            log.error("회사채 수익률 새로고침 실패", e);
            return ResponseEntity.ok(ApiResponse.error("새로고침 실패: " + e.getMessage()));
        }
    }

    /**
     * 특정 날짜의 회사채 수익률 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, List<Map<String, Object>>>>> getBondYields(
            @RequestParam(required = false) String date) {
        try {
            LocalDate targetDate = date != null ? 
                    LocalDate.parse(date) : LocalDate.now();
            
            log.info("회사채 수익률 조회 요청: {}", targetDate);
            
            // DB에서 데이터 조회
            List<BondYieldCurve> bondYields = bondYieldCrawlingService.getBondYieldsByDate(targetDate);
            
            log.info("조회된 데이터 개수: {}", bondYields.size());
            
            // 데이터가 없고 오늘 날짜라면 크롤링 시도
            if (bondYields.isEmpty() && targetDate.equals(LocalDate.now())) {
                log.info("오늘 날짜 데이터가 없음. 크롤링 시도...");
                
                try {
                    // 크롤링 실행 (오늘 날짜로 저장됨)
                    bondYieldCrawlingService.crawlAndSaveBondYields();
                    
                    // 다시 조회
                    bondYields = bondYieldCrawlingService.getBondYieldsByDate(targetDate);
                    log.info("크롤링 후 조회된 데이터 개수: {}", bondYields.size());
                    
                } catch (Exception e) {
                    log.error("크롤링 실패", e);
                    // 크롤링 실패 시에도 빈 데이터로 계속 진행 (에러 메시지는 프론트에서 처리)
                }
            }
            
            // 데이터를 테이블 형식으로 변환
            Map<String, List<Map<String, Object>>> result = formatBondYieldsForTable(bondYields, targetDate);
            
            return ResponseEntity.ok(ApiResponse.success(result));
            
        } catch (Exception e) {
            log.error("회사채 수익률 조회 실패", e);
            return ResponseEntity.ok(ApiResponse.error("조회 실패: " + e.getMessage()));
        }
    }

    /**
     * 가장 최근 데이터 조회
     */
    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<Map<String, List<Map<String, Object>>>>> getLatestBondYields() {
        try {
            List<BondYieldCurve> bondYields = bondYieldCrawlingService.getLatestBondYields();
            
            LocalDate date = bondYields.isEmpty() ? LocalDate.now() : bondYields.get(0).getAsOf();
            
            Map<String, List<Map<String, Object>>> result = formatBondYieldsForTable(bondYields, date);
            
            return ResponseEntity.ok(ApiResponse.success(result));
            
        } catch (Exception e) {
            log.error("최신 회사채 수익률 조회 실패", e);
            return ResponseEntity.ok(ApiResponse.error("조회 실패: " + e.getMessage()));
        }
    }

    /**
     * 테이블 형식으로 데이터 변환
     */
    private Map<String, List<Map<String, Object>>> formatBondYieldsForTable(
            List<BondYieldCurve> bondYields, LocalDate date) {
        
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        
        // 등급별로 그룹화
        Map<String, Map<Short, BondYieldCurve>> ratingMap = bondYields.stream()
                .collect(Collectors.groupingBy(
                        BondYieldCurve::getRating,
                        Collectors.toMap(
                                BondYieldCurve::getTenorMonths,
                                b -> b,
                                (a, b) -> a
                        )
                ));
        
        // 테이블 데이터 생성
        List<Map<String, Object>> tableData = new ArrayList<>();
        
        // 등급 순서 정의
        String[] ratings = {"국고채", "AAA", "AA+", "AA", "AA-", "A+", "A", "A-", "BBB+", "BBB", "BBB-"};
        Short[] tenors = {3, 6, 9, 12, 18, 24, 36, 60};
        
        for (String rating : ratings) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rating", rating);
            
            Map<Short, BondYieldCurve> tenorMap = ratingMap.getOrDefault(rating, new HashMap<>());
            
            for (Short tenor : tenors) {
                String key = convertTenorToKey(tenor);
                BondYieldCurve bond = tenorMap.get(tenor);
                
                if (bond != null) {
                    // 퍼센트로 변환 (0.0286 -> 2.86)
                    double yieldPercent = bond.getYieldRate().doubleValue() * 100;
                    row.put(key, String.format("%.2f", yieldPercent));
                } else {
                    row.put(key, "-");
                }
            }
            
            tableData.add(row);
        }
        
        result.put("data", tableData);
        result.put("meta", List.of(Map.of(
                "date", date.toString(),
                "count", bondYields.size()
        )));
        
        return result;
    }

    /**
     * Tenor를 키로 변환 (3->3M, 12->1Y, 18->1Y6M 등)
     */
    private String convertTenorToKey(Short tenor) {
        if (tenor < 12) {
            return tenor + "M";
        } else if (tenor == 12) {
            return "1Y";
        } else if (tenor == 18) {
            return "1Y6M";
        } else if (tenor % 12 == 0) {
            return (tenor / 12) + "Y";
        } else {
            int years = tenor / 12;
            int months = tenor % 12;
            return years + "Y" + months + "M";
        }
    }
}
