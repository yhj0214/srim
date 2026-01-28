package org.yhj.srim.service.dto;

import java.math.BigDecimal;
import java.util.Map;

public record FsRawBundle(
        Map<String, BigDecimal> curr,
        Map<String, BigDecimal> prev

) {
}
