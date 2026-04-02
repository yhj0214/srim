package org.yhj.srim.repository.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(name = "company_view_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Comment("회사 상세 조회 이벤트")
public class CompanyViewEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "view_event_id")
    @Comment("PK: 조회 이벤트 ID")
    private Long viewEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @Comment("FK → company.company_id")
    private Company company;

    @Column(name = "session_id", nullable = false, length = 128)
    @Comment("세션 식별자")
    private String sessionId;

    @Column(name = "viewed_at", nullable = false)
    @Comment("조회 시각")
    private LocalDateTime viewedAt;

    @PrePersist
    protected void onCreate() {
        if (viewedAt == null) {
            viewedAt = LocalDateTime.now();
        }
    }
}
