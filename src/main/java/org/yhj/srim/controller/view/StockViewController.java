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
import org.yhj.srim.service.domain.CompanyViewService;
import org.yhj.srim.service.domain.StockService;
import org.yhj.srim.service.dto.StockDto;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/stocks")
@RequiredArgsConstructor
@Slf4j
public class StockViewController {

    private final CompanyViewService companyViewService;
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
            HttpServletRequest request,
            HttpSession session,
            Model model) {

        StockDto stock = stockService.getByTicker(market, ticker);
        companyViewService.recordView(
                stock.getCompanyId(),
                session.getId(),
                extractClientIp(request),
                request.getHeader("User-Agent")
        );
        log.debug(stock.toString());
        model.addAttribute("stock", stock);
        return "stock-detail";
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

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
