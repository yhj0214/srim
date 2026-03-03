package org.yhj.srim.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SrimCalculateCommand {
    private Long companyId;
    private String basis;
    private Integer year;
    private String rating;
    private Integer tenor;
    private LocalDate asOf;
}
