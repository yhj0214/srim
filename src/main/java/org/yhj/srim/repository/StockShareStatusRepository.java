package org.yhj.srim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yhj.srim.repository.entity.StockShareStatus;

import java.time.LocalDate;
import java.util.Optional;

public interface StockShareStatusRepository extends JpaRepository<StockShareStatus, Long> {

    Optional<StockShareStatus> findByCompany_CompanyIdAndBsnsYearAndSe(
            Long companyId, Integer bsnsYear, String se
    );

    Optional<StockShareStatus> findTopByCompany_CompanyIdAndSettlementDateLessThanEqualAndSeOrderBySettlementDateDesc(Long companyId, LocalDate baseDate, String 합계);

    Optional<StockShareStatus> findTopByCompany_CompanyIdOrderByUpdatedAtDesc(Long companyId);
}