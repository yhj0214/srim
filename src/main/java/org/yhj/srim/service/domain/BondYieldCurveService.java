package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.client.dto.KisSpreadRow;
import org.yhj.srim.repository.BondYieldCurveRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BondYieldCurveService {

    private static final String SOURCE_KIS = "KIS";

    private final BondYieldCurveRepository bondYieldCurveRepository;

    @Transactional
    public int upsertDailyRows(LocalDate asOf, List<KisSpreadRow> rows) {
        int upsertCount = 0;

        for (KisSpreadRow row : rows) {
            String rating = normalizeRating(row.category());
            upsertCount += upsertTenor(asOf, rating, (short) 3, row.m3());
            upsertCount += upsertTenor(asOf, rating, (short) 6, row.m6());
            upsertCount += upsertTenor(asOf, rating, (short) 9, row.m9());
            upsertCount += upsertTenor(asOf, rating, (short) 12, row.y1());
            upsertCount += upsertTenor(asOf, rating, (short) 18, row.y1_6());
            upsertCount += upsertTenor(asOf, rating, (short) 24, row.y2());
            upsertCount += upsertTenor(asOf, rating, (short) 36, row.y3());
            upsertCount += upsertTenor(asOf, rating, (short) 60, row.y5());
        }

        return upsertCount;
    }

    private int upsertTenor(LocalDate asOf, String rating, short tenorMonths, BigDecimal ratePercent) {
        if (asOf == null || rating == null || rating.isBlank() || ratePercent == null) {
            return 0;
        }

        BigDecimal yieldRate = ratePercent.movePointLeft(2);
        return bondYieldCurveRepository.upsert(asOf, rating, tenorMonths, yieldRate, SOURCE_KIS);
    }

    private String normalizeRating(String category) {
        if (category == null) {
            return "UNKNOWN";
        }
        return category.trim();
    }
}
