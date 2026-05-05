package org.yhj.srim.controller.view;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.yhj.srim.service.domain.StockService;

@Controller
@RequiredArgsConstructor
@Slf4j
public class HomeViewController {

    private final StockService stockService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("title", "Home");
        model.addAttribute("totalStocks", stockService.count());

        return "index";
    }

}
