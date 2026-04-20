package org.yhj.srim.service.dto;

import java.util.List;

public record XbrlRawBundle(
        XbrlDocumentView document,
        List<XbrlContextView> contexts,
        List<XbrlFactView> facts
) {
}
