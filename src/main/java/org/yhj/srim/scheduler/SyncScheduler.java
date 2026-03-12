package org.yhj.srim.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.yhj.srim.service.facade.ScheduledSyncFacade;

@Component
@RequiredArgsConstructor
@Slf4j
public class SyncScheduler {

    private final ScheduledSyncFacade scheduledSyncFacade;

    @Scheduled(cron = "${app.scheduler.price.cron}", zone = "${app.scheduler.zone:Asia/Seoul}")
    public void syncDailyPrices() {
        log.info("[SCHED-PRICE] trigger");
        scheduledSyncFacade.syncDailyPrices();
    }

    @Scheduled(cron = "${app.scheduler.bond-yield.cron}", zone = "${app.scheduler.zone:Asia/Seoul}")
    public void syncDailyBondYields() {
        log.info("[SCHED-BOND] trigger");
        scheduledSyncFacade.syncDailyBondYields();
    }

    @Scheduled(cron = "${app.scheduler.financial-statement.cron}", zone = "${app.scheduler.zone:Asia/Seoul}")
    public void syncAnnualFinancialStatements() {
        log.info("[SCHED-FS] trigger");
        scheduledSyncFacade.syncAnnualFinancialStatements();
    }
}
