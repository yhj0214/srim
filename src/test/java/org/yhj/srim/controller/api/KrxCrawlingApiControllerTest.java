package org.yhj.srim.controller.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.yhj.srim.controller.dto.CrawlAllMarketsResult;
import org.yhj.srim.service.facade.ManagementFacade;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(controllers = KrxCrawlingApiController.class)
class KrxCrawlingApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ManagementFacade managementFacade;

    @Test
    @DisplayName("전체 크롤링 step1에 성공한다.")
    void all_crawling_step1_success() throws Exception {
        // given
        BDDMockito.given(managementFacade.collectMarketData())
                .willReturn(new CrawlAllMarketsResult(100, 80));

        // when & then
        mockMvc.perform(post("/api/crawling/krx/all/step1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.crawledCount").value(100))
                .andExpect(jsonPath("$.data.mappedCount").value(80));

    }

    @Test
    @DisplayName("단일 종목 reset 요청에 성공한다.")
    void single_stock_reset_success() throws Exception {
        mockMvc.perform(get("/api/crawling/krx/stocks/005930/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());

        BDDMockito.then(managementFacade).should().resetSingleCompanyByTickerKrx("005930");
    }
}
