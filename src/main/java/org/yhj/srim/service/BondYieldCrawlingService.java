package org.yhj.srim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.repository.BondYieldCurveRepository;
import org.yhj.srim.repository.entity.BondYieldCurve;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 한국신용평가 회사채 수익률 크롤링 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BondYieldCrawlingService {

    private final BondYieldCurveRepository bondYieldCurveRepository;
    
    private static final String BOND_YIELD_URL = "https://www.kisrating.com/ratingsStatistics/statics_spread.do";
    
    /**
     * 회사채 수익률 데이터 크롤링 및 저장
     */
    // 수익률 크롤링 링크 https://www.kisrating.com/ratingsStatistics/statics_spreadExcel.do&startDt=2025.11.06
    @Transactional
    public void crawlAndSaveBondYields() {
        try {
            log.info("회사채 수익률 크롤링 시작...");
            
            // 페이지 로드
            Document doc = Jsoup.connect(BOND_YIELD_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();
            
            // 테이블 파싱
            List<BondYieldCurve> bondYields = parseTable(doc);
            
            if (bondYields.isEmpty()) {
                log.error("크롤링된 데이터가 없습니다.");
                throw new RuntimeException("회사채 수익률 데이터 서버에 문제가 있어 데이터를 가져올 수 없습니다. 잠시 후 다시 시도해주세요.");
            }
            
            // 기존 오늘 날짜 데이터 삭제
            LocalDate today = LocalDate.now();
            bondYieldCurveRepository.deleteByAsOf(today);
            
            // 새 데이터 저장
            bondYieldCurveRepository.saveAll(bondYields);
            
            log.info("회사채 수익률 크롤링 완료: {} 건", bondYields.size());
            
        } catch (Exception e) {
            log.error("회사채 수익률 크롤링 실패", e);
            throw new RuntimeException("회사채 수익률 데이터 서버에 문제가 있어 데이터를 가져올 수 없습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * HTML 테이블 파싱
     */
    private List<BondYieldCurve> parseTable(Document doc) {
        List<BondYieldCurve> results = new ArrayList<>();
        LocalDate today = LocalDate.now();
        
        try {
            // 디버깅: HTML 구조 확인
            log.debug("HTML Title: {}", doc.title());
            
            // 여러 선택자 시도
            Element table = doc.select("table.tbl_st01").first();
            if (table == null) {
                table = doc.select("table.table").first();
            }
            if (table == null) {
                table = doc.select("table[class*=spread]").first();
            }
            if (table == null) {
                // 모든 테이블 찾기
                Elements tables = doc.select("table");
                log.info("찾은 테이블 개수: {}", tables.size());
                
                for (int i = 0; i < tables.size(); i++) {
                    Element t = tables.get(i);
                    log.info("테이블 #{} - class: {}, id: {}", i, t.className(), t.id());
                    
                    // 첫 번째 테이블 사용 (보통 메인 데이터 테이블)
                    if (i == 0 || t.select("tbody tr").size() > 5) {
                        table = t;
                        break;
                    }
                }
            }
            
            if (table == null) {
                log.error("테이블을 찾을 수 없습니다. 선택자를 확인하세요.");
                log.error("HTML 본문 (first 500 chars): {}", doc.body().html().substring(0, Math.min(500, doc.body().html().length())));
                return results;
            }
            
            log.info("테이블 찾음: class={}, id={}", table.className(), table.id());
            
            Elements rows = table.select("tbody tr");
            log.info("찾은 행 개수: {}", rows.size());
            
            if (rows.isEmpty()) {
                // tbody가 없을 수 있음
                rows = table.select("tr");
                log.info("tbody 없이 찾은 행 개수: {}", rows.size());
            }
            
            for (Element row : rows) {
                Elements cols = row.select("td");
                
                if (cols.isEmpty()) {
                    // th를 포함할 수 있음
                    cols = row.select("th, td");
                }
                
                log.debug("행 데이터: {} 개", cols.size());
                
                if (cols.size() < 9) {
                    continue;
                }
                
                String rating = cols.get(0).text().trim();
                log.debug("등급: {}", rating);
                
                // 헤더 행 건너뛰기
                if (rating.isEmpty() || rating.contains("만기") || rating.contains("기간")) {
                    continue;
                }
                
                // 등급명 정규화
                rating = normalizeRating(rating);
                
                // 각 만기별 수익률 파싱
                String[] maturities = {"3M", "6M", "9M", "1Y", "1Y6M", "2Y", "3Y", "5Y"};
                
                for (int i = 0; i < maturities.length && i + 1 < cols.size(); i++) {
                    String yieldText = cols.get(i + 1).text().trim();
                    
                    if (yieldText.isEmpty() || yieldText.equals("-")) {
                        continue;
                    }
                    
                    try {
                        double yieldValue = Double.parseDouble(yieldText);
                        
                        // 만기를 개월수로 변환
                        Short tenorMonths = convertMaturityToMonths(maturities[i]);
                        
                        BondYieldCurve bondYield = BondYieldCurve.builder()
                                .asOf(today)
                                .rating(rating)
                                .tenorMonths(tenorMonths)
                                .yieldRate(BigDecimal.valueOf(yieldValue / 100))  // 퍼센트를 소수로 변환 (2.86% -> 0.0286)
                                .source("KISRATING")
                                .build();
                        
                        results.add(bondYield);
                        log.debug("파싱 성공: {} {} = {}", rating, maturities[i], yieldValue);
                        
                    } catch (NumberFormatException e) {
                        log.warn("수익률 파싱 실패: {} - {}", rating, yieldText);
                    }
                }
            }
            
            log.info("총 {} 개 데이터 파싱 완료", results.size());
            
        } catch (Exception e) {
            log.error("테이블 파싱 중 오류", e);
        }
        
        return results;
    }
    
    /**
     * 등급명 정규화
     */
    private String normalizeRating(String rating) {
        rating = rating.toUpperCase()
                .replace("등급", "")
                .replace(" ", "")
                .trim();
        
        // 특수 케이스 처리
        if (rating.equals("국고채") || rating.contains("국고")) {
            return "국고채";
        }
        
        return rating;
    }
    
    /**
     * 만기 문자열을 개월수로 변환
     * 3M -> 3, 6M -> 6, 1Y -> 12, 1Y6M -> 18, 2Y -> 24 등
     */
    private Short convertMaturityToMonths(String maturity) {
        maturity = maturity.toUpperCase().trim();
        
        if (maturity.endsWith("M")) {
            // 3M, 6M, 9M
            return Short.parseShort(maturity.replace("M", ""));
        } else if (maturity.endsWith("Y")) {
            if (maturity.contains("Y") && maturity.length() > 2) {
                // 1Y6M -> 18
                String[] parts = maturity.split("Y");
                int years = Integer.parseInt(parts[0]);
                int months = parts.length > 1 ? Integer.parseInt(parts[1].replace("M", "")) : 0;
                return (short) (years * 12 + months);
            } else {
                // 1Y -> 12, 2Y -> 24
                return (short) (Integer.parseInt(maturity.replace("Y", "")) * 12);
            }
        }
        
        throw new IllegalArgumentException("지원하지 않는 만기 형식: " + maturity);
    }
    
    /**
     * 최신 데이터 조회 (크롤링 없이)
     */
    @Transactional(readOnly = true)
    public List<BondYieldCurve> getLatestBondYields() {
        return bondYieldCurveRepository.findByAsOf(LocalDate.now());
    }
    
    /**
     * 특정 날짜 데이터 조회
     */
    @Transactional(readOnly = true)
    public List<BondYieldCurve> getBondYieldsByDate(LocalDate date) {
        return bondYieldCurveRepository.findByAsOf(date);
    }
    
    /**
     * 가장 최근 데이터가 있는 날짜 조회
     */
    @Transactional(readOnly = true)
    public Optional<LocalDate> getLatestAvailableDate() {
        return bondYieldCurveRepository.findLatestAsOf();
    }
}
