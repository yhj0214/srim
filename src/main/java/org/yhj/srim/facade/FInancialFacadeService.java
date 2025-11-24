package org.yhj.srim.facade;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.service.FinancialService;
import org.yhj.srim.service.dto.FinancialTableDto;

@Service
@RequiredArgsConstructor
public class FInancialFacadeService {

    private final FinancialService financialService;


    @Transactional
    public FinancialTableDto getAnnualTable(Long stockId, int limit) {
        Company company = financialService.getOrCreateCompany(stockId);

        return null;
    }
}
