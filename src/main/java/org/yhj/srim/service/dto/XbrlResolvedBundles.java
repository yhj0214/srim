package org.yhj.srim.service.dto;

public record XbrlResolvedBundles(
        XbrlRawBundle current,
        XbrlRawBundle previous
) {
}
