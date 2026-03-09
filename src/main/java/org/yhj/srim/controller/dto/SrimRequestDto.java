package org.yhj.srim.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.yhj.srim.service.dto.SrimCalculateCommand;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SrimRequestDto {
    private Integer year;
    private String rating;
    private Integer tenor;

    public SrimCalculateCommand toCommand(Long companyId, LocalDate asOf) {
        return SrimCalculateCommand.builder()
                .companyId(companyId)
                .year(year)
                .rating(rating)
                .tenor(tenor)
                .asOf(asOf)
                .build();
    }
}
