package org.yhj.srim.service.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CommonError;
import org.yhj.srim.common.exception.code.CrawlingError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnualXbrlRunPolicyTest {

    @InjectMocks
    AnnualXbrlRunPolicy annualXbrlRunPolicy;

    @Mock
    AnnualXbrlMetricProcessor annualXbrlMetricProcessor;

    @Test
    @DisplayName("CFS 공시 미존재는 OFS fallback 대상이다.")
    void shouldFallbackToOfs_returnsTrueForDisclosureNotFound() {
        assertThat(annualXbrlRunPolicy.shouldFallbackToOfs(
                "CFS",
                new CustomException(CrawlingError.DART_DISCLOSURE_NOT_FOUND)
        )).isTrue();
    }

    @Test
    @DisplayName("CFS XBRL 미존재는 OFS fallback 대상이다.")
    void shouldFallbackToOfsOnMissingXbrl_returnsTrueForMissingXbrl() {
        assertThat(annualXbrlRunPolicy.shouldFallbackToOfsOnMissingXbrl(
                "CFS",
                "CFS",
                new CustomException(CrawlingError.DART_XBRL_NOT_AVAILABLE)
        )).isTrue();
    }

    @Test
    @DisplayName("처리 단계에서는 저장된 OFS raw가 있으면 OFS로 fallback 한다.")
    void resolveAnnualProcessingFsDiv_fallsBackToOfs() {
        when(annualXbrlMetricProcessor.hasAnnualXbrlRaw(7L, 2024, "CFS")).thenReturn(false);
        when(annualXbrlMetricProcessor.hasAnnualXbrlRaw(7L, 2024, "OFS")).thenReturn(true);

        String resolved = annualXbrlRunPolicy.resolveAnnualProcessingFsDiv(7L, 2024, "CFS");

        assertThat(resolved).isEqualTo("OFS");
    }

    @Test
    @DisplayName("CFS가 아니면 공시 미존재여도 OFS fallback 대상이 아니다.")
    void shouldFallbackToOfs_returnsFalseForOfsRequest() {
        assertThat(annualXbrlRunPolicy.shouldFallbackToOfs(
                "OFS",
                new CustomException(CommonError.INVALID_INPUT)
        )).isFalse();
    }
}
