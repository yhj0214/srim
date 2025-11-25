//package org.yhj.srim.event;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.context.event.EventListener;
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Propagation;
//import org.springframework.transaction.annotation.Transactional;
//import org.yhj.srim.service.DartCrawlingService;
//
///**
// * 재무 데이터 이벤트 리스너
// */
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class FinancialDataEventListener {
//
//    private final DartCrawlingService financialCrawlingService;
//
//    /**
//     * 재무 데이터 누락 시 크롤링 시도
//     */
//    @EventListener
//    @Async
//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    public void handleFinancialDataMissing(FinancialDataMissingEvent event) {
//        log.info("재무 데이터 누락 이벤트 수신 - companyId: {}, ticker: {}",
//                event.getCompanyId(), event.getTickerKrx());
//
//        try {
//            int count = financialCrawlingService.crawlAndSaveFinancialData(
//                    event.getCompanyId(),
//                    event.getTickerKrx()
//            );
//
//            if (count > 0) {
//                log.info("이벤트 기반 크롤링 성공: {} 건 저장", count);
//            } else {
//                log.warn("이벤트 기반 크롤링 데이터 없음");
//            }
//        } catch (Exception e) {
//            log.error("이벤트 기반 재무 데이터 크롤링 실패", e);
//        }
//    }
//}
