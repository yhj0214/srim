package org.yhj.srim.service.domain;

import org.yhj.srim.service.dto.XbrlFactView;
import org.yhj.srim.service.dto.XbrlRawBundle;

import java.util.Optional;

public interface XbrlNetIncomeAttributionFallbackRule {

    Optional<XbrlNetIncomeAttributionFallbackResolver.ResolvedNetIncomeAttribution> resolve(
            XbrlRawBundle bundle,
            XbrlFactView totalProfitLoss
    );
}
