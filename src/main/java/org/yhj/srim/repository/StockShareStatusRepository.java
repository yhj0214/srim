package org.yhj.srim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yhj.srim.repository.entity.StockShareStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockShareStatusRepository extends JpaRepository<StockShareStatus, Long> {

    Optional<StockShareStatus> findByCompany_CompanyIdAndBsnsYearAndSe(
            Long companyId, Integer bsnsYear, String se
    );

    List<StockShareStatus> findByCompany_CompanyIdAndBsnsYearAndSeIn(
            Long companyId, Integer bsnsYear, List<String> seList
    );

    Optional<StockShareStatus> findTopByCompany_CompanyIdAndSettlementDateLessThanEqualAndSeOrderBySettlementDateDesc(Long companyId, LocalDate baseDate, String 합계);

    Optional<StockShareStatus> findTopByCompany_CompanyIdOrderByUpdatedAtDesc(Long companyId);

    long deleteByCompany_CompanyIdAndBsnsYear(Long companyId, Integer bsnsYear);
}