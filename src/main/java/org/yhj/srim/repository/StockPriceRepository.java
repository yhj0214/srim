package org.yhj.srim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.yhj.srim.repository.entity.StockPrice;

import java.time.LocalDate;
import java.util.List;

public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {
    
    /**
     * 회사 ID로 주가 데이터 존재 여부 확인
     */
    boolean existsByCompany_CompanyId(Long companyId);

    /**
     * 회사 ID로 모든 주가 데이터 조회
     */
    List<StockPrice> findByCompany_companyId(Long companyId);

    /**
     * 회사 ID와 기간으로 주가 데이터 조회 (거래일 기준, 날짜 오름차순)
     */
    List<StockPrice> findByCompany_CompanyIdAndTradeDateBetweenOrderByTradeDateAsc(
            Long companyId, LocalDate startDate, LocalDate endDate);

    /**
     * 회사의 최소 거래일 조회
     */
    @Query("SELECT MIN(sp.tradeDate) FROM StockPrice sp WHERE sp.company.companyId = :companyId")
    LocalDate findMinTradeDateByCompany(@Param("companyId") Long companyId);

    /**
     * 회사의 최대 거래일 조회
     */
    @Query("SELECT MAX(sp.tradeDate) FROM StockPrice sp WHERE sp.company.companyId = :companyId")
    LocalDate findMaxTradeDateByCompany(@Param("companyId") Long companyId);

    List<StockPrice> findByCompany_companyIdOrderByTradeDateAsc(Long companyId);
}
