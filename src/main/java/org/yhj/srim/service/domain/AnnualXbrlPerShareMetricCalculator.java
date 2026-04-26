package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.repository.StockShareStatusRepository;
import org.yhj.srim.repository.entity.ShareClassType;
import org.yhj.srim.repository.entity.StockShareStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnnualXbrlPerShareMetricCalculator {
    private final StockShareStatusRepository stockShareStatusRepository;

    public Map<String, BigDecimal> calculate(Long companyId, Map<String, BigDecimal> raw, int fiscalYear) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return result;
        }

        BigDecimal netIncOwner = raw.get("NET_INC_OWNER");
        Optional<BigDecimal> epsOpt = calcEps(companyId, fiscalYear, netIncOwner);
        epsOpt.ifPresent(eps -> result.put("EPS", eps));

        return result;
    }

    private Optional<BigDecimal> calcEps(Long companyId, int fiscalYear, BigDecimal netIncOwner) {
        if (netIncOwner == null) {
            log.info("[FS-DB][EPS] netIncOwner is null - companyId={}, year={}", companyId, fiscalYear);
            return Optional.empty();
        }

        Optional<BigDecimal> eps = findTotalIssuedShares(companyId, fiscalYear)
                .filter(shares -> shares.compareTo(BigDecimal.ZERO) > 0)
                .map(shares -> netIncOwner.divide(shares, 2, RoundingMode.HALF_UP));

        if (eps.isEmpty()) {
            log.info("[FS-DB][EPS] common shares not found or zero - companyId={}, year={}", companyId, fiscalYear);
        } else {
            log.debug("[FS-DB][EPS] ok - companyId={}, year={}, netIncOwner={}, eps={}",
                    companyId, fiscalYear, netIncOwner, eps.get());
        }

        return eps;
    }

    private Optional<BigDecimal> findTotalIssuedShares(Long companyId, int fiscalYear) {
        List<StockShareStatus> statuses =
                stockShareStatusRepository.findByCompany_CompanyIdAndBsnsYearAndShareClassTypeIn(
                        companyId, fiscalYear, List.of(ShareClassType.COMMON, ShareClassType.PREFERRED)
                );

        if (statuses.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal total = BigDecimal.ZERO;
        for (StockShareStatus status : statuses) {
            BigDecimal shares = resolveIssuedShares(status);
            if (shares != null) {
                total = total.add(shares);
            }
        }

        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        return Optional.of(total);
    }

    private BigDecimal resolveIssuedShares(StockShareStatus status) {
        Long istc = status.getIstcTotqy();
        if (istc != null && istc > 0L) {
            return BigDecimal.valueOf(istc);
        }
        return null;
    }
}
