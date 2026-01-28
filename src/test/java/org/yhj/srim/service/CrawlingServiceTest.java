package org.yhj.srim.service;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.client.DartClient;
import org.yhj.srim.client.NaverClient;
import org.yhj.srim.client.dto.DaliyPrice;
import org.yhj.srim.client.dto.DartFsRow;
import org.yhj.srim.fixture.CrawlFixture;
import org.yhj.srim.repository.CompanyRepository;
import org.yhj.srim.repository.StockCodeRepository;
import org.yhj.srim.repository.StockPriceRepository;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.StockCode;
import org.yhj.srim.repository.entity.StockPrice;
import org.yhj.srim.service.crawl.CrawlingService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.yhj.srim.fixture.CrawlFixture.*;

@Transactional
@SpringBootTest
class CrawlingServiceTest {

    @Autowired
    CrawlingService crawlingService;
    @Autowired
    StockCodeRepository stockCodeRepository;
    @Autowired
    CompanyRepository companyRepository;

    @MockitoBean
    NaverClient naverClient;
    
    @Autowired
    private StockPriceRepository stockPriceRepository;
    @MockitoBean
    private DartClient dartClient;

    @Test
    void crawlAnnualFinancialSucccess(){
        String corpCode = "00126380";
        int year = 2021;

        List<DartFsRow> rows = createFsRows();
        when(dartClient.fetchAnnualFinancialStatements(corpCode, year)).thenReturn(rows);

        List<DartFsRow> result = crawlingService.crawlAnnualFinancial(corpCode, year);

        Assertions.assertThat(result).isNotEmpty();

    }

}