package org.yhj.srim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yhj.srim.repository.entity.FailedJob;
import org.yhj.srim.repository.entity.FailedJobStatus;
import org.yhj.srim.repository.entity.FailedJobType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FailedJobRepository extends JpaRepository<FailedJob, Long> {

    Optional<FailedJob> findByDedupeKey(String dedupeKey);

    List<FailedJob> findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            Collection<FailedJobStatus> statuses,
            LocalDateTime nextRetryAt
    );

    List<FailedJob> findTop100ByJobTypeAndStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            FailedJobType jobType,
            Collection<FailedJobStatus> statuses,
            LocalDateTime nextRetryAt
    );
}
