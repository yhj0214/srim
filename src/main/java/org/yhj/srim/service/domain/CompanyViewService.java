package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.StockError;
import org.yhj.srim.repository.CompanyRepository;
import org.yhj.srim.repository.CompanyViewEventRepository;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.CompanyViewEvent;
import org.yhj.srim.service.dto.CompanyViewCountDto;
import org.yhj.srim.service.dto.PopularStockDto;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CompanyViewService {

    private static final long DEDUPE_MINUTES = 30L;
    private static final List<String> BOT_USER_AGENT_KEYWORDS = List.of(
            "bot",
            "crawler",
            "spider",
            "preview",
            "headless",
            "slackbot",
            "facebookexternalhit",
            "twitterbot",
            "discordbot",
            "whatsapp",
            "curl",
            "python-requests"
    );

    private final CompanyRepository companyRepository;
    private final CompanyViewEventRepository companyViewEventRepository;

    @Transactional
    public void recordView(Long companyId, String sessionId, String ipAddress, String userAgent) {
        if (companyId == null || sessionId == null || sessionId.isBlank() || ipAddress == null || ipAddress.isBlank()) {
            return;
        }
        if (isBotUserAgent(userAgent)) {
            log.debug("회사 조회수 봇 요청 스킵 - companyId={}, userAgent={}", companyId, userAgent);
            return;
        }

        LocalDateTime dedupeThreshold = LocalDateTime.now().minusMinutes(DEDUPE_MINUTES);
        boolean alreadyCounted = companyViewEventRepository
                .existsByCompany_CompanyIdAndSessionIdAndIpAddressAndViewedAtAfter(
                        companyId, sessionId, ipAddress, dedupeThreshold
                );
        if (alreadyCounted) {
            log.debug("회사 조회수 중복 스킵 - companyId={}, sessionId={}, ip={}", companyId, sessionId, ipAddress);
            return;
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(StockError.COMPANY_NOT_FOUND, "companyId=" + companyId));

        companyViewEventRepository.save(CompanyViewEvent.builder()
                .company(company)
                .sessionId(sessionId)
                .ipAddress(ipAddress)
                .userAgent(trimUserAgent(userAgent))
                .viewedAt(LocalDateTime.now())
                .build());
    }

    private boolean isBotUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return false;
        }

        String normalized = userAgent.toLowerCase(Locale.ROOT);
        return BOT_USER_AGENT_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    private String trimUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return userAgent.length() <= 300 ? userAgent : userAgent.substring(0, 300);
    }

    public List<PopularStockDto> findPopularStocks(int days, int limit) {
        int safeDays = Math.max(days, 1);
        int safeLimit = Math.max(limit, 1);
        LocalDateTime since = LocalDateTime.now().minusDays(safeDays);

        List<CompanyViewCountDto> counts = companyViewEventRepository.findPopularCompanyCountsSince(
                since,
                PageRequest.of(0, safeLimit)
        );
        if (counts.isEmpty()) {
            return List.of();
        }

        List<Long> companyIds = counts.stream()
                .map(CompanyViewCountDto::companyId)
                .toList();

        Map<Long, Long> countByCompanyId = new LinkedHashMap<>();
        for (CompanyViewCountDto count : counts) {
            countByCompanyId.put(count.companyId(), count.viewCount());
        }

        List<Company> companies = companyRepository.findAllWithStockCodeByCompanyIdIn(companyIds);
        Map<Long, Company> companyById = new LinkedHashMap<>();
        for (Company company : companies) {
            companyById.put(company.getCompanyId(), company);
        }

        List<PopularStockDto> result = new ArrayList<>();
        for (Long companyId : companyIds) {
            Company company = companyById.get(companyId);
            if (company == null || company.getStockCode() == null) {
                continue;
            }

            result.add(PopularStockDto.from(company, countByCompanyId.get(companyId)));
        }

        return result;
    }
}
