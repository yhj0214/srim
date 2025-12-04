package org.yhj.srim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yhj.srim.repository.entity.StockPrice;

import java.util.List;

public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {
    boolean existsByCompany_CompanyId(Long companyId);

    List<StockPrice> findByCompany_companyId(Long companyId);
}
