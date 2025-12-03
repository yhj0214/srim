package org.yhj.srim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.yhj.srim.repository.entity.FinPeriod;

import java.util.List;
import java.util.Optional;

@Repository
public interface FinPeriodRepository extends JpaRepository<FinPeriod, Long> {

    /**
     * 회사의 모든 기간 조회 (최신순)
     */
    List<FinPeriod> findByCompany_CompanyIdOrderByFiscalYearDescFiscalQuarterDesc(Long companyId);

    /**
     * 회사의 연간 기간 조회 (최신순)
     */
    @Query("SELECT p FROM FinPeriod p WHERE p.company.companyId = :companyId AND p.periodType = 'YEAR' ORDER BY p.fiscalYear DESC")
    List<FinPeriod> findYearlyPeriods(@Param("companyId") Long companyId);

    /**
     * 회사의 분기 기간 조회 (최신순)
     */
    @Query("SELECT p FROM FinPeriod p WHERE p.company.companyId = :companyId AND p.periodType = 'QTR' ORDER BY p.fiscalYear DESC, p.fiscalQuarter DESC")
    List<FinPeriod> findQuarterlyPeriods(@Param("companyId") Long companyId);

    /**
     * 회사의 요청 연도 직전 최근 N개 연간 기간 조회
     */
    @Query(value = """
        SELECT * 
        FROM fin_period 
        WHERE company_id = :companyId 
          AND period_type = 'YEAR' 
          AND fiscal_year <= :baseYear
        ORDER BY fiscal_year DESC 
        LIMIT :limit
        """, nativeQuery = true)
    List<FinPeriod> findRecentYearlyPeriods(@Param("companyId") Long companyId,@Param("baseYear") int baseYear, @Param("limit") int limit);

    /**
     * 회사의 최근 N개 분기 기간 조회
     */
    @Query(value = "SELECT * FROM fin_period WHERE company_id = :companyId AND period_type = 'QTR' ORDER BY fiscal_year DESC, fiscal_quarter DESC LIMIT :limit", nativeQuery = true)
    List<FinPeriod> findRecentQuarterlyPeriods(@Param("companyId") Long companyId, @Param("limit") int limit);

    /**
     * 특정 조건으로 기간 조회
     */
    Optional<FinPeriod> findByCompany_CompanyIdAndPeriodTypeAndFiscalYearAndFiscalQuarter(
            Long companyId,
            String periodType,
            Integer fiscalYear,
            Integer fiscalQuarter
    );

    List<FinPeriod> findByCompany_CompanyIdAndPeriodTypeAndFiscalYearBetweenAndIsEstimateOrderByFiscalYearDesc(Long companyId, String year, int startYear, int currentYear, boolean b);

    Optional<FinPeriod> findByCompany_CompanyIdAndPeriodTypeAndFiscalYearAndIsEstimate(Long companyId, String year, int fiscalYear, boolean b);

    FinPeriod findByCompany_CompanyIdAndFiscalYear(Long companyId,int baseYear);
}
