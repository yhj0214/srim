package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.client.DartReportType;
import org.yhj.srim.client.dto.DartFilingRow;
import org.yhj.srim.repository.DartFsFilingRepository;
import org.yhj.srim.repository.entity.DartFsFiling;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DartFsFilingService {

    private final DartFsFilingRepository dartFsFilingRepository;

    @Transactional
    public DartFsFiling saveAnnualFilingMetadata(String corpCode, Long companyId, int fiscalYear,
                                                 DartFilingRow row, String fsDiv) {
        return saveFilingMetadata(corpCode, companyId, fiscalYear, row, DartReportType.ANNUAL, fsDiv);
    }

    @Transactional
    public DartFsFiling saveFilingMetadata(String corpCode,
                                           Long companyId,
                                           int fiscalYear,
                                           DartFilingRow row,
                                           DartReportType reportType,
                                           String fsDiv) {
        Optional<DartFsFiling> existing = dartFsFilingRepository.findByRceptNoAndReprtCodeAndFsDiv(
                row.getRceptNo(),
                reportType.code(),
                fsDiv
        );
        if (existing.isPresent()) {
            return existing.get();
        }

        DartFsFiling filing = DartFsFiling.builder()
                .corpCode(corpCode)
                .companyId(companyId)
                .rceptNo(row.getRceptNo())
                .reprtCode(reportType.code())
                .bsnsYear(fiscalYear)
                .fsDiv(fsDiv)
                .reportTp(reportType.label())
                .rceptDt(parseDate(row.getRceptDt()))
                .note(buildNote(row))
                .build();

        return dartFsFilingRepository.save(filing);
    }

    private LocalDate parseDate(String rceptDt) {
        if (rceptDt == null || rceptDt.length() != 8) {
            return null;
        }
        int yyyy = Integer.parseInt(rceptDt.substring(0, 4));
        int mm = Integer.parseInt(rceptDt.substring(4, 6));
        int dd = Integer.parseInt(rceptDt.substring(6, 8));
        return LocalDate.of(yyyy, mm, dd);
    }

    private String buildNote(DartFilingRow row) {
        if (row.getReportNm() == null && row.getRm() == null) {
            return null;
        }
        if (row.getReportNm() == null) {
            return row.getRm();
        }
        if (row.getRm() == null) {
            return row.getReportNm();
        }
        return row.getReportNm() + " | " + row.getRm();
    }
}
