package org.yhj.srim.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient kisWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl("https://www.kisrating.com")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/142.0.0.0 Safari/537.36")
                .build();
    }

    @Bean(name = "dartRestTemplate")
    public RestTemplate dartRestTemplate(
            RestTemplateBuilder builder,
            @Value("${dart.api.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${dart.api.read-timeout-ms:10000}") long readTimeoutMs,
            @Value("${app.crawl.userAgent:Mozilla/5.0}") String userAgent
    ) {

        ClientHttpRequestInterceptor userAgentInterceptor = (request, body, execution) -> {
            request.getHeaders().set(HttpHeaders.USER_AGENT, userAgent);
            return execution.execute(request, body);
        };


        return builder
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ofMillis(readTimeoutMs))
                .additionalInterceptors(List.of(userAgentInterceptor))
                .build();
    }

    @Bean(name = "bondYieldTaskExecutor")
    public ThreadPoolTaskExecutor bondYieldTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("bond-yield-");
        executor.initialize();
        return executor;
    }

}
