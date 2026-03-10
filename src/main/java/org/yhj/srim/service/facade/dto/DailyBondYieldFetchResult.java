package org.yhj.srim.service.facade.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.yhj.srim.client.dto.KisSpreadRow;

import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public class DailyBondYieldFetchResult {
    private final LocalDate date;
    private final List<KisSpreadRow> rows;
    private final boolean success;

    public static DailyBondYieldFetchResult success(LocalDate date, List<KisSpreadRow> rows) {
        return new DailyBondYieldFetchResult(date, rows, true);
    }

    public static DailyBondYieldFetchResult failure(LocalDate date) {
        return new DailyBondYieldFetchResult(date, List.of(), false);
    }
}
