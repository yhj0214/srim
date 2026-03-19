package org.yhj.srim.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@Entity
@Table(name = "failed_job")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Comment("재시도 대상 실패 작업 큐")
public class FailedJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "failed_job_id", nullable = false)
    @Comment("PK: 실패 작업 ID")
    private Long failedJobId;

    @Column(name = "dedupe_key", nullable = false, length = 200, unique = true)
    @Comment("중복 적재 방지 키")
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 50)
    @Comment("작업 유형")
    private FailedJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Comment("작업 상태")
    private FailedJobStatus status;

    @Column(name = "target_id")
    @Comment("재시도 대상 숫자 ID")
    private Long targetId;

    @Column(name = "target_key", length = 100)
    @Comment("재시도 대상 문자열 키")
    private String targetKey;

    @Column(name = "target_date")
    @Comment("재시도 대상 날짜")
    private LocalDate targetDate;

    @Column(name = "payload", columnDefinition = "json")
    @Comment("작업 파라미터 JSON")
    private String payload;

    @Column(name = "error_code", length = 50)
    @Comment("마지막 실패 코드")
    private String errorCode;

    @Column(name = "error_message", length = 500)
    @Comment("마지막 실패 메시지")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    @Comment("재시도 횟수")
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "max_retry_count", nullable = false)
    @Comment("최대 재시도 횟수")
    @Builder.Default
    private int maxRetryCount = 5;

    @Column(name = "next_retry_at", nullable = false)
    @Comment("다음 재시도 시각")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_attempt_at")
    @Comment("마지막 시도 시각")
    private LocalDateTime lastAttemptAt;

    @Column(name = "processed_at")
    @Comment("최종 처리 완료 시각")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Comment("생성 시각")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Comment("수정 시각")
    private LocalDateTime updatedAt;

    public static FailedJob pending(
            FailedJobType jobType,
            String dedupeKey,
            Long targetId,
            String targetKey,
            LocalDate targetDate,
            String payload,
            String errorCode,
            String errorMessage,
            LocalDateTime nextRetryAt,
            int maxRetryCount
    ) {
        return FailedJob.builder()
                .jobType(jobType)
                .dedupeKey(dedupeKey)
                .status(FailedJobStatus.PENDING)
                .targetId(targetId)
                .targetKey(targetKey)
                .targetDate(targetDate)
                .payload(payload)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .nextRetryAt(nextRetryAt)
                .maxRetryCount(maxRetryCount)
                .build();
    }

    public void markRetrying(LocalDateTime attemptedAt) {
        this.status = FailedJobStatus.RETRYING;
        this.lastAttemptAt = attemptedAt;
    }

    public void markDone(LocalDateTime processedAt) {
        this.status = FailedJobStatus.DONE;
        this.processedAt = processedAt;
        this.nextRetryAt = processedAt;
    }

    public void refreshPending(String errorCode, String errorMessage, LocalDateTime nextRetryAt) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.nextRetryAt = nextRetryAt;
        this.status = FailedJobStatus.PENDING;
        this.processedAt = null;
    }

    public void reschedule(String errorCode, String errorMessage, LocalDateTime nextRetryAt) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.nextRetryAt = nextRetryAt;
        this.retryCount += 1;
        this.status = this.retryCount >= this.maxRetryCount ? FailedJobStatus.FAILED : FailedJobStatus.PENDING;
        if (this.status == FailedJobStatus.FAILED) {
            this.processedAt = nextRetryAt;
        }
    }

    public boolean isRetryExhausted() {
        return retryCount >= maxRetryCount;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.status == null) {
            this.status = FailedJobStatus.PENDING;
        }
        if (this.nextRetryAt == null) {
            this.nextRetryAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
