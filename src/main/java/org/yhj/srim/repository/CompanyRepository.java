package org.yhj.srim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.StockCode;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    /**
     * StockCode의 ID로 Company 조회
     */
    Optional<Company> findByStockCode_StockId(Long stockId);

    /**
     * 시장과 티커로 Company 조회 (StockCode 조인)
     */
    @Query("SELECT c FROM Company c JOIN c.stockCode s WHERE s.market = :market AND s.tickerKrx = :ticker")
    Optional<Company> findByMarketAndTicker(@Param("market") String market, @Param("ticker") String ticker);
    
    /**
     * 티커로 StockCode 조회 (Company 테이블을 통해)
     */
    @Query("SELECT c.stockCode FROM Company c WHERE c.stockCode.tickerKrx = :ticker")
    Optional<StockCode> findStockCodeByTicker(@Param("ticker") String ticker);
}
