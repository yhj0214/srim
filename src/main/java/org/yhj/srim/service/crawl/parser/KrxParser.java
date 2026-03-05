package org.yhj.srim.service.crawl.parser;

import org.yhj.srim.service.crawl.dto.StockCodeDraft;

import java.util.List;

public interface KrxParser {
    boolean supports(String content);

    List<StockCodeDraft> parse(String content, String marketType);
}
