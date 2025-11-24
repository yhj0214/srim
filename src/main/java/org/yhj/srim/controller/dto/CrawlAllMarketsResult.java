package org.yhj.srim.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
public class CrawlAllMarketsResult {
    int crawledCount;
    int mappedCount;
}
