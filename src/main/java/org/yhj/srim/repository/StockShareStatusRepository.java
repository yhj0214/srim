package org.yhj.srim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yhj.srim.repository.entity.ShareClassType;
import org.yhj.srim.repository.entity.StockShareStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockShareStatusRepository extends JpaRepository<StockShareStatus, Long> {

    List<StockShareStatus> findByCompany_CompanyIdAndBsnsYearAndShareClassTypeIn(
            Long companyId, Integer bsnsYear, List<ShareClassType> shareClassTypes
    );

    Optional<StockShareStatus> findTopByCompany_CompanyIdAndSettlementDateLessThanEqualAndShareClassTypeOrderBySettlementDateDesc(
            Long companyId, LocalDate baseDate, ShareClassType shareClassType
    );

    Optional<StockShareStatus> findTopByCompany_CompanyIdAndShareClassTypeOrderBySettlementDateDesc(
            Long companyId, ShareClassType shareClassType
    );

    Optional<StockShareStatus> findTopByCompany_CompanyIdOrderByUpdatedAtDesc(Long companyId);

    Optional<StockShareStatus> findTopByCompany_CompanyIdAndShareClassTypeAndBsnsYearLessThanEqualOrderByBsnsYearDesc(
            Long companyId, ShareClassType shareClassType, Integer bsnsYear
    );

    long deleteByCompany_CompanyIdAndBsnsYear(Long companyId, Integer bsnsYear);

    long deleteByCompany_CompanyId(Long companyId);
}
