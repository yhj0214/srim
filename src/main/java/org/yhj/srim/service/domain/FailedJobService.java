package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CrawlingError;
import org.yhj.srim.repository.FailedJobRepository;
import org.yhj.srim.repository.entity.FailedJob;
import org.yhj.srim.repository.entity.FailedJobStatus;
import org.yhj.srim.repository.entity.FailedJobType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FailedJobService {

    private static final int DEFAULT_MAX_RETRY_COUNT = 5;
    private static final long DEFAULT_RETRY_DELAY_MINUTES = 5L;

    private final FailedJobRepository failedJobRepository;

    @Transactional
    public void enqueueBondYieldFailure(LocalDate date, String detail) {
        String dedupeKey = buildBondYieldDedupeKey(date);
        LocalDateTime nextRetryAt = calculateNextRetryAt();
        String payload = "{\"date\":\"" + date + "\"}";

        failedJobRepository.findByDedupeKey(dedupeKey)
                .ifPresentOrElse(
                        failedJob -> failedJob.refreshPending(
                                CrawlingError.KIS_REQUEST_FAILED.getCode(),
                                detail,
                                nextRetryAt
                        ),
                        () -> failedJobRepository.save(FailedJob.pending(
                                FailedJobType.BOND_YIELD,
                                dedupeKey,
                                null,
                                null,
                                date,
                                payload,
                                CrawlingError.KIS_REQUEST_FAILED.getCode(),
                                detail,
                                nextRetryAt,
                                DEFAULT_MAX_RETRY_COUNT
                        ))
                );
    }

    @Transactional(readOnly = true)
    public List<FailedJob> findDueBondYieldJobs() {
        return failedJobRepository.findTop100ByJobTypeAndStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                FailedJobType.BOND_YIELD,
                List.of(FailedJobStatus.PENDING),
                LocalDateTime.now()
        );
    }

    @Transactional
    public void markRetrying(FailedJob failedJob) {
        failedJob.markRetrying(LocalDateTime.now());
        failedJobRepository.save(failedJob);
    }

    @Transactional
    public void markDone(FailedJob failedJob) {
        failedJob.markDone(LocalDateTime.now());
        failedJobRepository.save(failedJob);
    }

    @Transactional
    public void rescheduleBondYieldFailure(FailedJob failedJob, CustomException e) {
        failedJob.reschedule(
                e.getErrorCode().getCode(),
                buildFailureDetail(e),
                calculateNextRetryAt()
        );
        failedJobRepository.save(failedJob);
    }

    private String buildFailureDetail(CustomException e) {
        if (e.getDetail() == null || e.getDetail().isBlank()) {
            return e.getMessage();
        }
        return e.getMessage() + " (" + e.getDetail() + ")";
    }

    private LocalDateTime calculateNextRetryAt() {
        return LocalDateTime.now().plusMinutes(DEFAULT_RETRY_DELAY_MINUTES);
    }

    private String buildBondYieldDedupeKey(LocalDate date) {
        return "BOND_YIELD:" + date;
    }
}
