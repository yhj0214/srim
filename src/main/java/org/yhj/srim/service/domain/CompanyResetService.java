package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.repository.CompanyViewEventRepository;
import org.yhj.srim.repository.CompanyRepository;
import org.yhj.srim.repository.DartFsFilingRepository;
import org.yhj.srim.repository.DartFsLineRepository;
import org.yhj.srim.repository.FinMetricValueRepository;
import org.yhj.srim.repository.FinPeriodRepository;
import org.yhj.srim.repository.StockPriceRepository;
import org.yhj.srim.repository.StockShareStatusRepository;
import org.yhj.srim.repository.entity.Company;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CompanyResetService {

    private final CompanyViewEventRepository companyViewEventRepository;
    private final CompanyRepository companyRepository;
    private final StockPriceRepository stockPriceRepository;
    private final FinMetricValueRepository finMetricValueRepository;
    private final FinPeriodRepository finPeriodRepository;
    private final StockShareStatusRepository stockShareStatusRepository;
    private final DartFsLineRepository dartFsLineRepository;
    private final DartFsFilingRepository dartFsFilingRepository;

    public void resetByStockId(Long stockId) {
        Company company = companyRepository.findByStockCode_StockId(stockId).orElse(null);
        if (company == null) {
            log.info("company 없음 stockId={}, ", stockId);
            return;
        }

        Long companyId = company.getCompanyId();
        companyViewEventRepository.deleteByCompany_CompanyId(companyId);
        stockPriceRepository.deleteByCompany_CompanyId(companyId);
        finMetricValueRepository.deleteByCompanyId(companyId);
        finPeriodRepository.deleteByCompany_CompanyId(companyId);
        stockShareStatusRepository.deleteByCompany_CompanyId(companyId);
        dartFsLineRepository.deleteByCompanyId(companyId);
        dartFsFilingRepository.deleteByCompanyId(companyId);
        companyRepository.delete(company);

        log.info("회사 reset 완료 - stockId={}, companyId={}", stockId, companyId);
    }
}
