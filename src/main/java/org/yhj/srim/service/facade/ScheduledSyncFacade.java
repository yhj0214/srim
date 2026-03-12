package org.yhj.srim.service.facade;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ScheduledSyncFacade {

    public void syncDailyPrices() {
        log.info("[SCHED-PRICE] start");
        // TODO: 전체 회사 대상 주가 동기화 로직 연결
        log.info("[SCHED-PRICE] done");
    }

    public void syncDailyBondYields() {
        log.info("[SCHED-BOND] start");
        // TODO: 회사채 수익률 동기화 로직 연결
        log.info("[SCHED-BOND] done");
    }

    public void syncAnnualFinancialStatements() {
        log.info("[SCHED-FS] start");
        // TODO: 사업보고서 시즌 대상 재무/주식수 갱신 로직 연결
        log.info("[SCHED-FS] done");
    }
}
