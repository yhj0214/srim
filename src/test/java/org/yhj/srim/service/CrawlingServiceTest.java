package org.yhj.srim.service;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.client.NaverClient;
import org.yhj.srim.client.dto.DaliyPrice;
import org.yhj.srim.repository.CompanyRepository;
import org.yhj.srim.repository.StockCodeRepository;
import org.yhj.srim.repository.StockPriceRepository;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.StockCode;
import org.yhj.srim.repository.entity.StockPrice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;

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
    private StockPriceService stockPriceService;
    @Autowired
    private StockPriceRepository stockPriceRepository;

    @Test
    void crawlingStockPrice_Success() {

        // given
        StockCode stockCode = StockCode.builder()
                .tickerKrx("005930")
                .companyName("삼성전자")
                .build();

        stockCode = stockCodeRepository.save(stockCode);

        Company company = Company.builder()
                .stockCode(stockCode)
                .createdAt(LocalDateTime.now())
                .currency("KRW")
                .build();

        company = companyRepository.save(company);

        Long companyId = company.getCompanyId();

        LocalDate start = LocalDate.of(2025, 11, 24);
        LocalDate end = LocalDate.of(2025, 12, 4);
        List<DaliyPrice> mockPrices = createPriceSampleData();

        // stub
        given(naverClient.fetchDailyPrices("005930", start, end))
                .willReturn(mockPrices);

        // when
        int savedCount = crawlingService.crawlingStockPrice(companyId, start, end);

        // then
        Assertions.assertThat(savedCount).isEqualTo(mockPrices.size());
        List<StockPrice> saved = stockPriceRepository.findByCompany_companyId(companyId);
        Assertions.assertThat(saved).hasSize(mockPrices.size());
    }

    private List<DaliyPrice> createPriceSampleData() {
        return List.of(
                new DaliyPrice(LocalDate.of(2025, 12, 4),
                        bd("103900"), bd("104900"), bd("103200"), bd("104750"), 9823135L),
                new DaliyPrice(LocalDate.of(2025, 12, 3),
                        bd("104700"), bd("105500"), bd("104000"), bd("104500"), 14697927L),
                new DaliyPrice(LocalDate.of(2025, 12, 2),
                        bd("101200"), bd("103500"), bd("101000"), bd("103400"), 13649487L),
                new DaliyPrice(LocalDate.of(2025, 12, 1),
                        bd("102000"), bd("102800"), bd("99900"), bd("100800"), 10905526L),
                new DaliyPrice(LocalDate.of(2025, 11, 28),
                        bd("103800"), bd("103800"), bd("100500"), bd("100500"), 15292277L),
                new DaliyPrice(LocalDate.of(2025, 11, 27),
                        bd("104100"), bd("105500"), bd("102700"), bd("103500"), 16453004L),
                new DaliyPrice(LocalDate.of(2025, 11, 26),
                        bd("100500"), bd("102900"), bd("99300"), bd("102800"), 21314975L),
                new DaliyPrice(LocalDate.of(2025, 11, 25),
                        bd("101400"), bd("101400"), bd("97800"), bd("99300"), 16110054L),
                new DaliyPrice(LocalDate.of(2025, 11, 24),
                        bd("97800"), bd("99000"), bd("96200"), bd("96700"), 29831172L)
        );
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}