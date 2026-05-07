package org.yhj.srim.service.domain;

import org.springframework.stereotype.Service;
import org.yhj.srim.repository.entity.Company;

@Service
public class QuarterXbrlCollector {

    public Long collectQuarterInputs(Company company, int fiscalYear, int fiscalQuarter, String fsDiv) {
        throw new UnsupportedOperationException("Quarter XBRL input collection is not implemented yet.");
    }
}
