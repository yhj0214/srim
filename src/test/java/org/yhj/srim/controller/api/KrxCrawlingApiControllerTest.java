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
import org.yhj.srim.service.crawl.KrxStockCrawlingService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = KrxCrawlingApiController.class)
class KrxCrawlingApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ManagementFacade managementFacade;
    @MockitoBean
    KrxStockCrawlingService krxStockCrawlingService;

    @Test
    @DisplayName("전체 크롤링 step1에 성공한다.")
    void all_crawling_step1_success() throws Exception {
        // given
        BDDMockito.given(managementFacade.step1MarketSync())
                .willReturn(new CrawlAllMarketsResult(100, 80));

        // when & then
        mockMvc.perform(post("/api/crawling/krx/all/step1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.crawledCount").value(100))
                .andExpect(jsonPath("$.data.mappedCount").value(80));

    }
}