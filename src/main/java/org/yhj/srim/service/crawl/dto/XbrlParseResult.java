package org.yhj.srim.service.crawl.dto;

import java.util.List;

public record XbrlParseResult(
        List<XbrlParsedContext> contexts,
        List<XbrlParsedFact> facts,
        String taxonomyVersion
) {
}
