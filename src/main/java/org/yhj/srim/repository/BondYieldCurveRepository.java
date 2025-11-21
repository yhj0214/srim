package org.yhj.srim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.yhj.srim.repository.entity.BondYieldCurve;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BondYieldCurveRepository extends JpaRepository<BondYieldCurve, Long> {

    /**
     * 특정 등급, 만기의 가장 최근 수익률 조회 (네이밍 메서드 사용)
     */
    Optional<BondYieldCurve> findFirstByRatingAndTenorMonthsOrderByAsOfDesc(
            String rating, 
            Short tenorMonths
    );

    /**
     * 특정 등급, 만기, 기준일의 수익률 조회
     */
    Optional<BondYieldCurve> findByRatingAndTenorMonthsAndAsOf(
            String rating, 
            Short tenorMonths, 
            LocalDate asOf
    );

    /**
     * 특정 등급, 만기의 특정 날짜 이전 가장 최근 수익률 조회 (네이밍 메서드 사용)
     */
    Optional<BondYieldCurve> findFirstByRatingAndTenorMonthsAndAsOfLessThanEqualOrderByAsOfDesc(
            String rating,
            Short tenorMonths,
            LocalDate asOf
    );

    /**
     * 특정 기준일의 모든 수익률 조회
     */
    List<BondYieldCurve> findByAsOf(LocalDate asOf);

    /**
     * 특정 기준일의 데이터 삭제
     */
    void deleteByAsOf(LocalDate asOf);

    /**
     * 가장 최근 기준일 조회
     */
    @Query("SELECT MAX(b.asOf) FROM BondYieldCurve b")
    Optional<LocalDate> findLatestAsOf();
}
