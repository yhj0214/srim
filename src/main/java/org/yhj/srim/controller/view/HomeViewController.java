package org.yhj.srim.controller.view;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.yhj.srim.service.StockService;

@Controller
@RequiredArgsConstructor
@Slf4j
public class HomeViewController {

    private final StockService stockService;

    /**
     * 홈 페이지 (검색창)
     */
    @GetMapping("/")
    public String home(Model model) {
        long totalStocks = stockService.count();
        model.addAttribute("totalStocks", totalStocks);
        return "index";
    }

    /**
     * 회사채 수익률 페이지
     */
    @GetMapping("/bond-yields")
    public String bondYields() {
        return "bond-yields";
    }

    /**
     * S-RIM 적정가 계산 페이지
     */
    @GetMapping("/srim")
    public String srim() {
        return "srim";
    }
}
