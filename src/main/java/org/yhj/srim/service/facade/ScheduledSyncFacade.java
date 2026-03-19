package org.yhj.srim.service.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.repository.entity.FailedJob;
import org.yhj.srim.service.domain.FailedJobService;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScheduledSyncFacade {

    private final FailedJobService failedJobService;
    private final FinancialFacadeService financialFacadeService;

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

    public void retryFailedBondYields() {
        List<FailedJob> jobs = failedJobService.findDueBondYieldJobs();
        if (jobs.isEmpty()) {
            log.debug("[SCHED-BOND-RETRY] no due jobs");
            return;
        }

        log.info("[SCHED-BOND-RETRY] start size={}", jobs.size());
        for (FailedJob job : jobs) {
            if (job.getTargetDate() == null) {
                log.warn("[SCHED-BOND-RETRY] skip missing targetDate jobId={}", job.getFailedJobId());
                continue;
            }

            failedJobService.markRetrying(job);
            try {
                financialFacadeService.retryBondYieldForDate(job.getTargetDate());
                failedJobService.markDone(job);
            } catch (CustomException e) {
                failedJobService.rescheduleBondYieldFailure(job, e);
                log.warn("[SCHED-BOND-RETRY] failed date={} detail={}", job.getTargetDate(), e.getDetail());
            } catch (Exception e) {
                failedJobService.rescheduleBondYieldFailure(
                        job,
                        new CustomException(org.yhj.srim.common.exception.code.CrawlingError.KIS_REQUEST_FAILED, e.getMessage())
                );
                log.error("[SCHED-BOND-RETRY] unexpected failure date={}", job.getTargetDate(), e);
            }
        }
        log.info("[SCHED-BOND-RETRY] done size={}", jobs.size());
    }
}
