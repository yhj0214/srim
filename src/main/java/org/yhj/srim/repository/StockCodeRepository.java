package org.yhj.srim.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.yhj.srim.repository.entity.StockCode;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockCodeRepository extends JpaRepository<StockCode, Long> {

    /**
     * 시장과 티커로 종목 조회
     */
    Optional<StockCode> findByMarketAndTickerKrx(String market, String tickerKrx);

    /**
     * 회사명 또는 티커로 검색 (페이징)
     */
    @Query("SELECT s FROM StockCode s WHERE s.companyName LIKE %:keyword% OR s.tickerKrx LIKE %:keyword%")
    Page<StockCode> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    Page<StockCode> findAll(Pageable pageable);


    @Query("SELECT s.stockId FROM StockCode s")
    List<Long> findAllStockIds();

    @Query("SELECT s.stockId FROM StockCode s WHERE s.tickerKrx = :tickerKrx")
    Optional<Long> findStockIdByTickerKrx(@Param("tickerKrx") String tickerKrx);
}
