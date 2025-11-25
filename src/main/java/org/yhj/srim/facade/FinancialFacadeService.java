package org.yhj.srim.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.client.DartClient;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.StockErrorCode;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.service.CrawlingService;
import org.yhj.srim.service.FinancialService;
import org.yhj.srim.service.dto.FinancialTableDto;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialFacadeService {

    private final FinancialService financialService;
    private final CrawlingService crawlingService;

    /**
     * 1. company 조회, 없을 시 생성
     * 2. 재무제표, 주식 수 크롤링 및 저장
     * 3. 저장된 값들로 지표 계산 및 financialTableDto생성
     */
    @Transactional
    public FinancialTableDto getAnnualTable(Long stockId, int limit) {
        Company company = financialService.getOrCreateCompany(stockId);

        String corpCode = company.getStockCode().getDartCorpCode();

        if (corpCode == null || corpCode.length() != 8) {
            throw new CustomException(StockErrorCode.DART_CODE_NOT_FOUND);
        }

        int currentYear = LocalDate.now().getYear();
//        int startYear = currentYear - limit + 1;
        int startYear = currentYear - 2;

        log.info("연간 테이블 조회 - companyId={}, corpCode={}, year {}~{}",
                company.getCompanyId(), corpCode, startYear, currentYear);

        for (int year = currentYear; year >= startYear; year--) {
            log.debug("{}년 크롤링 진행", year);
            // 재무제표 크롤링 및 저장
            crawlingService.crawlAndSaveAnnualFinancial(corpCode, company.getCompanyId(), year);
            // 주식수 크롤링 및 저장
            crawlingService.crawlAndSaveShareStatus(corpCode, company.getCompanyId(), year);
        }



        return null;
    }
}
