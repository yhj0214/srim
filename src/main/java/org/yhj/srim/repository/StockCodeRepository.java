package org.yhj.srim.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.yhj.srim.repository.entity.StockCode;

import java.util.Optional;

@Repository
public interface StockCodeRepository extends JpaRepository<StockCode, Long> {

    /**
     * 시장과 티커로 종목 조회
     */
    Optional<StockCode> findByMarketAndTickerKrx(String market, String tickerKrx);

    /**
     * 티커로 종목 조회
     */
    Optional<StockCode> findByTickerKrx(String tickerKrx);

    /**
     * 회사명 또는 티커로 검색 (페이징)
     */
    @Query("SELECT s FROM StockCode s WHERE s.companyName LIKE %:keyword% OR s.tickerKrx LIKE %:keyword%")
    Page<StockCode> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 시장별 종목 조회
     */
    Page<StockCode> findByMarket(String market, Pageable pageable);

    /**
     * 전체 종목 조회 (페이징)
     */
    Page<StockCode> findAll(Pageable pageable);
}
