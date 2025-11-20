package com.gdu.zeus.ops.workorder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP客户端配置
 * 提供RestTemplate和WebClient两种方式
 */
@Configuration
public class HttpClientConfig {
    private final WorkOrderApiProperties apiProperties;

    public HttpClientConfig(WorkOrderApiProperties apiProperties) {
        this.apiProperties = apiProperties;
    }

    /**
     * 配置RestTemplate (同步调用)
     */
    @Bean("workOrderRestTemplate")
    public RestTemplate workOrderRestTemplate() {
        // 使用 Spring 内置的 SimpleClientHttpRequestFactory
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(apiProperties.getConnectTimeout());
        factory.setReadTimeout(apiProperties.getReadTimeout());

        RestTemplate restTemplate = new RestTemplate(factory);
        // 添加认证拦截器
        WorkOrderApiProperties.Auth auth = apiProperties.getAuth();
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();

        if ("BEARER".equalsIgnoreCase(auth.getType()) && auth.getBearerToken() != null) {
            interceptors.add((request, body, execution) -> {
                request.getHeaders().set("Authorization", "Bearer " + auth.getBearerToken());
                return execution.execute(request, body);
            });
        } else if ("API_KEY".equalsIgnoreCase(auth.getType()) && auth.getApiKey() != null) {
            interceptors.add((request, body, execution) -> {
                request.getHeaders().set(auth.getApiKeyHeader(), auth.getApiKey());
                return execution.execute(request, body);
            });
        } else if ("BASIC".equalsIgnoreCase(auth.getType()) && auth.getUsername() != null && auth.getPassword() != null) {
            interceptors.add(new BasicAuthenticationInterceptor(auth.getUsername(), auth.getPassword()));
        }
        restTemplate.setInterceptors(interceptors);

        return restTemplate;
    }
//    /**
//     * 配置WebClient (响应式调用)
//     */
//    @Bean("workOrderWebClient")
//    public WebClient workOrderWebClient() {
//        HttpClient httpClient = HttpClient.create()
//                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, apiProperties.getConnectTimeout())
//                .doOnConnected(conn -> conn
//                        .addHandlerLast(new ReadTimeoutHandler(apiProperties.getReadTimeout(), TimeUnit.MILLISECONDS))
//                        .addHandlerLast(new WriteTimeoutHandler(apiProperties.getReadTimeout(), TimeUnit.MILLISECONDS)));
//
//        WebClient.Builder builder = WebClient.builder()
//                .baseUrl(apiProperties.getBaseUrl())
//                .clientConnector(new ReactorClientHttpConnector(httpClient));
//
//        // 添加认证配置
//        WorkOrderApiProperties.Auth auth = apiProperties.getAuth();
//        switch (auth.getType().toUpperCase()) {
//            case "BEARER":
//                if (auth.getBearerToken() != null) {
//                    builder.defaultHeader("Authorization", "Bearer " + auth.getBearerToken());
//                }
//                break;
//            case "API_KEY":
//                if (auth.getApiKey() != null) {
//                    builder.defaultHeader(auth.getApiKeyHeader(), auth.getApiKey());
//                }
//                break;
//            case "BASIC":
//                // Basic认证通过filter添加
//                break;
//        }
//
//        return builder.build();
//    }
}
