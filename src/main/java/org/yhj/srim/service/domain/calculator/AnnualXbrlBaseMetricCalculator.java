package org.yhj.srim.service.domain.calculator;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AnnualXbrlBaseMetricCalculator {

    public Map<String, BigDecimal> calculate(Map<String, BigDecimal> raw) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();

        if (raw == null || raw.isEmpty()) {
            return result;
        }

        putIfNotNull(result, "SALES", raw.get("SALES"));
        putIfNotNull(result, "OP_INC", raw.get("OP_INC"));
        putIfNotNull(result, "NET_INC", raw.get("NET_INC"));
        putIfNotNull(result, "NET_INC_OWNER", raw.get("NET_INC_OWNER"));
        putIfNotNull(result, "NET_INC_NONCONT", raw.get("NET_INC_NONCONT"));
        putIfNotNull(result, "TOTAL_LIABILITIES", raw.get("TOTAL_LIABILITIES"));
        putIfNotNull(result, "TOTAL_EQUITY", raw.get("TOTAL_EQUITY"));
        putIfNotNull(result, "TOTAL_EQUITY_OWNER", raw.get("TOTAL_EQUITY_OWNER"));

        return result;
    }

    private void putIfNotNull(Map<String, BigDecimal> map, String key, BigDecimal value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
