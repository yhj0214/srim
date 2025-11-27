package org.yhj.srim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.yhj.srim.repository.entity.FinMetricValue;

import java.util.List;
import java.util.Optional;

@Repository
public interface FinMetricValueRepository extends JpaRepository<FinMetricValue, Long> {

    /**
     * 특정 회사의 특정 기간 모든 지표 조회
     */
    List<FinMetricValue> findByCompanyIdAndPeriodId(Long companyId, Long periodId);

    /**
     * 특정 회사의 특정 기간 특정 지표 조회
     */
    Optional<FinMetricValue> findByCompanyIdAndPeriodIdAndMetricCode(Long companyId, Long periodId, String metricCode);

    /**
     * 특정 회사의 여러 기간에 대한 특정 지표 조회
     */
    @Query("SELECT v FROM FinMetricValue v WHERE v.companyId = :companyId AND v.periodId IN :periodIds AND v.metricCode = :metricCode")
    List<FinMetricValue> findByCompanyIdAndPeriodIdsAndMetricCode(
            @Param("companyId") Long companyId,
            @Param("periodIds") List<Long> periodIds,
            @Param("metricCode") String metricCode
    );

    /**
     * 특정 회사의 여러 기간에 대한 모든 지표 조회
     */
    @Query("SELECT v FROM FinMetricValue v WHERE v.companyId = :companyId AND v.periodId IN :periodIds")
    List<FinMetricValue> findByCompanyIdAndPeriodIds(
            @Param("companyId") Long companyId,
            @Param("periodIds") List<Long> periodIds
    );

    List<FinMetricValue> findByCompanyIdAndPeriodIdIn(Long companyId, List<Long> periodIds);
}
