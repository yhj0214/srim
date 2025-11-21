package org.yhj.srim.controller.view;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;
import org.yhj.srim.service.StockService;
import org.yhj.srim.service.dto.StockDto;

import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/stocks")
@RequiredArgsConstructor
@Slf4j
public class StockViewController {

    private final StockService stockService;

    /**
     * 종목 목록 페이지
     */
    @GetMapping
    public String list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("companyName"));
        Page<StockDto> stocks = stockService.search(q, pageable);
        
        model.addAttribute("stocks", stocks);
        model.addAttribute("keyword", q);
        model.addAttribute("currentPage", page);
        
        return "stocks";
    }

    /**
     * 종목 상세 페이지
     */
    @GetMapping("/{market}-{ticker}")
    public String detail(
            @PathVariable String market,
            @PathVariable String ticker,
            Model model) {
        
        try {
            StockDto stock = stockService.getByTicker(market, ticker);
            log.debug(stock.toString());
            model.addAttribute("stock", stock);
            return "stock-detail";
        } catch (IllegalArgumentException e) {
            log.error("종목을 찾을 수 없음: market={}, ticker={}", market, ticker);
            model.addAttribute("error", e.getMessage());
            return "error";
        }
    }

    /**
     * 검색 리다이렉트 (URL 인코딩 처리)
     */
    @GetMapping("/search")
    public String search(@RequestParam String q) {
        // 한글 검색어 URL 인코딩 처리
        String encodedQuery = UriUtils.encode(q, StandardCharsets.UTF_8);
        return "redirect:/stocks?q=" + encodedQuery;
    }
}
