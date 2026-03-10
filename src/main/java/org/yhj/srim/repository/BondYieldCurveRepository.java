package org.yhj.srim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.repository.entity.BondYieldCurve;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BondYieldCurveRepository extends JpaRepository<BondYieldCurve, Long> {


    /**
     * 특정 등급, 만기의 특정 날짜 이전 가장 최근 수익률 조회 (네이밍 메서드 사용)
     */
    Optional<BondYieldCurve> findFirstByRatingAndTenorMonthsAndAsOfLessThanEqualOrderByAsOfDesc(
            String rating,
            Short tenorMonths,
            LocalDate asOf
    );


    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO bond_yield_curve
            (as_of, rating, tenor_months, yield_rate, source, created_at)
        VALUES
            (:asOf, :rating, :tenorMonths, :yieldRate, :source, NOW())
        ON DUPLICATE KEY UPDATE
            yield_rate = VALUES(yield_rate),
            source     = VALUES(source)
        """, nativeQuery = true)
    int upsert(@Param("asOf") LocalDate asOf,
               @Param("rating") String rating,
               @Param("tenorMonths") short tenorMonths,
               @Param("yieldRate") BigDecimal yieldRate,
               @Param("source") String source);

    List<BondYieldCurve> findByRatingAndTenorMonthsAndAsOfBetweenOrderByAsOfAsc(String rating, Short tenorMonths, LocalDate startDate, LocalDate endDate);
}
