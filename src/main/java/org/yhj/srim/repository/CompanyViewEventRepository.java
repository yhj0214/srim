package org.yhj.srim.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.yhj.srim.repository.entity.CompanyViewEvent;
import org.yhj.srim.service.dto.CompanyViewCountDto;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CompanyViewEventRepository extends JpaRepository<CompanyViewEvent, Long> {

    boolean existsByCompany_CompanyIdAndSessionIdAndIpAddressAndViewedAtAfter(
            Long companyId, String sessionId, String ipAddress, LocalDateTime viewedAt
    );

    long deleteByCompany_CompanyId(Long companyId);

    @Query("""
            SELECT new org.yhj.srim.service.dto.CompanyViewCountDto(
                e.company.companyId,
                COUNT(e)
            )
            FROM CompanyViewEvent e
            WHERE e.viewedAt >= :since
            GROUP BY e.company.companyId
            ORDER BY COUNT(e) DESC
            """)
    List<CompanyViewCountDto> findPopularCompanyCountsSince(@Param("since") LocalDateTime since, Pageable pageable);
}
