package org.yhj.srim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.repository.StockPriceRepository;
import org.yhj.srim.service.dto.StockPriceDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 주가 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockPriceService {

    private final StockPriceRepository stockPriceRepository;
    /**
     * 주가 데이터 조회
     * 주가 DB 조회, 없는 데이터 네이버 크롤링 + 저장
     * 
     * @param companyId 회사 ID
     * @param startDate 시작일 (nullable)
     * @param endDate 종료일 (nullable)
     * @return 주가 그래프 데이터
     */
//    public StockPriceDto getStockPriceData(Long companyId, LocalDate startDate, LocalDate endDate) {
//        log.info("=== getStockPriceData 호출 ===");
//        log.info("companyId: {}", companyId);
//        log.info("startDate: {}", startDate);
//        log.info("endDate: {}", endDate);
//
//        // 기간 설정 (dafault 최근 1년)
//        LocalDate end = endDate != null ? endDate : LocalDate.now();
//        LocalDate start = startDate != null ? startDate : end.minusYears(1);
//
//        // 더미 주가 데이터 생성 (최근 1년치)
//        List<StockPriceDto.PriceData> priceDataList = generateDummyPriceData(start, end);
//
//        // 더미 시나리오 데이터 생성
//        StockPriceDto.ScenarioData scenarioData = StockPriceDto.ScenarioData.builder()
//                .scenario0(new BigDecimal("65000"))   // 초과이익 지속
//                .scenario10(new BigDecimal("60000"))  // 10% 감소
//                .scenario20(new BigDecimal("55000"))  // 20% 감소
//                .scenario30(new BigDecimal("50000"))  // 30% 감소
//                .scenario50(new BigDecimal("40000"))  // 50% 감소
//                .build();
//
//        return StockPriceDto.builder()
//                .priceData(priceDataList)
//                .scenarioData(scenarioData)
//                .build();
//    }
    
    /**
     * 더미 주가 데이터 생성 (날짜별 적정주가 포함)
     */
//    private List<StockPriceDto.PriceData> generateDummyPriceData(LocalDate start, LocalDate end) {
//        List<StockPriceDto.PriceData> dataList = new ArrayList<>();
//
//        BigDecimal basePrice = new BigDecimal("50000");
//
//        // 적정주가 기준값 (시작점)
//        BigDecimal baseFairValue0 = new BigDecimal("62000");   // 초과이익 지속
//        BigDecimal baseFairValue10 = new BigDecimal("57000");  // 10% 감소
//        BigDecimal baseFairValue20 = new BigDecimal("52000");  // 20% 감소
//        BigDecimal baseFairValue30 = new BigDecimal("47000");  // 30% 감소
//        BigDecimal baseFairValue50 = new BigDecimal("37000");  // 50% 감소
//
//        LocalDate currentDate = start;
//
//        while (!currentDate.isAfter(end)) {
//            // 주말 제외
//            if (currentDate.getDayOfWeek().getValue() < 6) {
//                // 주가 랜덤하게 등락 생성 (-3% ~ +3%)
//                double changeRate = (Math.random() * 0.06) - 0.03;
//                BigDecimal change = basePrice.multiply(BigDecimal.valueOf(changeRate));
//                BigDecimal close = basePrice.add(change);
//
//                // 고가, 저가, 시가 생성
//                BigDecimal high = close.multiply(BigDecimal.valueOf(1 + Math.random() * 0.02));
//                BigDecimal low = close.multiply(BigDecimal.valueOf(1 - Math.random() * 0.02));
//                BigDecimal open = low.add(high.subtract(low).multiply(BigDecimal.valueOf(Math.random())));
//
//                // 거래량 랜덤 생성 (1천만 ~ 5천만)
//                Long volume = (long) (10000000 + Math.random() * 40000000);
//
//                // 적정주가도 느리게 변동 (-0.5% ~ +0.5%)
//                double fairChangeRate = (Math.random() * 0.01) - 0.005;
//                baseFairValue0 = baseFairValue0.multiply(BigDecimal.valueOf(1 + fairChangeRate));
//                baseFairValue10 = baseFairValue10.multiply(BigDecimal.valueOf(1 + fairChangeRate));
//                baseFairValue20 = baseFairValue20.multiply(BigDecimal.valueOf(1 + fairChangeRate));
//                baseFairValue30 = baseFairValue30.multiply(BigDecimal.valueOf(1 + fairChangeRate));
//                baseFairValue50 = baseFairValue50.multiply(BigDecimal.valueOf(1 + fairChangeRate));
//
//                // 날짜별 적정주가
//                StockPriceDto.FairValues fairValues = StockPriceDto.FairValues.builder()
//                        .scenario0(baseFairValue0.setScale(0, BigDecimal.ROUND_HALF_UP))
//                        .scenario10(baseFairValue10.setScale(0, BigDecimal.ROUND_HALF_UP))
//                        .scenario20(baseFairValue20.setScale(0, BigDecimal.ROUND_HALF_UP))
//                        .scenario30(baseFairValue30.setScale(0, BigDecimal.ROUND_HALF_UP))
//                        .scenario50(baseFairValue50.setScale(0, BigDecimal.ROUND_HALF_UP))
//                        .build();
//
//                StockPriceDto.PriceData priceData = StockPriceDto.PriceData.builder()
//                        .date(currentDate)
//                        .open(open.setScale(0, BigDecimal.ROUND_HALF_UP))
//                        .high(high.setScale(0, BigDecimal.ROUND_HALF_UP))
//                        .low(low.setScale(0, BigDecimal.ROUND_HALF_UP))
//                        .close(close.setScale(0, BigDecimal.ROUND_HALF_UP))
//                        .volume(volume)
//                        .fairValues(fairValues)
//                        .build();
//
//                dataList.add(priceData);
//
//                // 다음 날의 base price를 오늘의 종가로 설정
//                basePrice = close;
//            }
//
//            currentDate = currentDate.plusDays(1);
//        }
//
//        return dataList;
//    }

    public boolean hasAnyPrice(Long companyId) {
        return stockPriceRepository.existsByCompany_CompanyId(companyId);
    }
}
