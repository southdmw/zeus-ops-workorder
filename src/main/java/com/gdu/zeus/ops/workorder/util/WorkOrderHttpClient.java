package com.gdu.zeus.ops.workorder.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdu.zeus.ops.workorder.client.dto.WorkOrderApiDto;
import com.gdu.zeus.ops.workorder.config.WorkOrderApiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

/**
 * 通用HTTP客户端包装器
 * 提供统一的错误处理、重试、日志等功能
 */
@Component
public class WorkOrderHttpClient {
    
    private static final Logger logger = LoggerFactory.getLogger(WorkOrderHttpClient.class);
    
    private final RestTemplate restTemplate;
    private final WorkOrderApiProperties apiProperties;
    private final ObjectMapper objectMapper;
    
    public WorkOrderHttpClient(RestTemplate workOrderRestTemplate,
                               WorkOrderApiProperties apiProperties,
                               ObjectMapper objectMapper) {
        this.restTemplate = workOrderRestTemplate;
        this.apiProperties = apiProperties;
        this.objectMapper = objectMapper;
    }
    
    /**
     * 同步GET请求
     */
    public <T> WorkOrderApiDto.ApiResponse<T> get(String endpoint, 
                                                   Map<String, Object> queryParams,
                                                   Class<T> responseType) {
        try {
            logger.info("发送GET请求: endpoint={}, params={}", endpoint, queryParams);
            
            String url = buildUrl(endpoint, queryParams);
            HttpHeaders headers = createHeaders();
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ParameterizedTypeReference<WorkOrderApiDto.ApiResponse<T>> typeRef = 
                    ParameterizedTypeReference.forType(
                            objectMapper.getTypeFactory()
                                    .constructParametricType(WorkOrderApiDto.ApiResponse.class, responseType)
                    );
            
            ResponseEntity<WorkOrderApiDto.ApiResponse<T>> response = 
                    restTemplate.exchange(url, HttpMethod.GET, entity, typeRef);
            
            WorkOrderApiDto.ApiResponse<T> result = response.getBody();
            logger.info("GET请求成功: endpoint={}, code={}", endpoint, result != null ? result.getCode() : null);
            
            return result;
            
        } catch (RestClientException e) {
            logger.error("GET请求失败: endpoint={}, error={}", endpoint, e.getMessage(), e);
            throw new WorkOrderApiException("API调用失败: " + endpoint, e);
        }
    }
    
    /**
     * 同步POST请求
     */
    public <T, R> WorkOrderApiDto.ApiResponse<R> post(String endpoint,
                                                       T requestBody,
                                                       Class<R> responseType) {
        try {
            logger.info("发送POST请求: endpoint={}, body={}", endpoint, requestBody);
            
            HttpHeaders headers = createHeaders();
            HttpEntity<T> entity = new HttpEntity<>(requestBody, headers);
            
            ParameterizedTypeReference<WorkOrderApiDto.ApiResponse<R>> typeRef = 
                    ParameterizedTypeReference.forType(
                            objectMapper.getTypeFactory()
                                    .constructParametricType(WorkOrderApiDto.ApiResponse.class, responseType)
                    );
            
            ResponseEntity<WorkOrderApiDto.ApiResponse<R>> response = 
                    restTemplate.exchange(endpoint, HttpMethod.POST, entity, typeRef);
            
            WorkOrderApiDto.ApiResponse<R> result = response.getBody();
            logger.info("POST请求成功: endpoint={}, code={}", endpoint, result != null ? result.getCode() : null);
            
            return result;
            
        } catch (RestClientException e) {
            logger.error("POST请求失败: endpoint={}, error={}", endpoint, e.getMessage(), e);
            throw new WorkOrderApiException("API调用失败: " + endpoint, e);
        }
    }
    
    /**
     * 响应式GET请求 (支持重试)
     */
//    public <T> Mono<WorkOrderApiDto.ApiResponse<T>> getAsync(String endpoint,
//                                                              Map<String, Object> queryParams,
//                                                              Class<T> responseType) {
//        logger.info("发送异步GET请求: endpoint={}, params={}", endpoint, queryParams);
//
//        String url = buildUrl(endpoint, queryParams);
//
//        ParameterizedTypeReference<WorkOrderApiDto.ApiResponse<T>> typeRef =
//                ParameterizedTypeReference.forType(
//                        objectMapper.getTypeFactory()
//                                .constructParametricType(WorkOrderApiDto.ApiResponse.class, responseType)
//                );
//
//        Mono<WorkOrderApiDto.ApiResponse<T>> request = webClient.get()
//                .uri(url)
//                .retrieve()
//                .bodyToMono(typeRef)
//                .doOnSuccess(result -> logger.info("异步GET请求成功: endpoint={}, code={}",
//                        endpoint, result != null ? result.getCode() : null))
//                .doOnError(error -> logger.error("异步GET请求失败: endpoint={}, error={}",
//                        endpoint, error.getMessage()));
//
//        // 添加重试逻辑
//        if (apiProperties.isRetryEnabled()) {
//            request = request.retryWhen(Retry.backoff(apiProperties.getMaxRetries(), Duration.ofSeconds(1))
//                    .filter(this::shouldRetry)
//                    .doBeforeRetry(retrySignal ->
//                            logger.warn("重试请求: endpoint={}, attempt={}", endpoint, retrySignal.totalRetries() + 1))
//            );
//        }
//
//        return request.onErrorMap(e -> new WorkOrderApiException("异步API调用失败: " + endpoint, e));
//    }
    
    /**
     * 响应式POST请求 (支持重试)
     */
//    public <T, R> Mono<WorkOrderApiDto.ApiResponse<R>> postAsync(String endpoint,
//                                                                   T requestBody,
//                                                                   Class<R> responseType) {
//        logger.info("发送异步POST请求: endpoint={}, body={}", endpoint, requestBody);
//
//        ParameterizedTypeReference<WorkOrderApiDto.ApiResponse<R>> typeRef =
//                ParameterizedTypeReference.forType(
//                        objectMapper.getTypeFactory()
//                                .constructParametricType(WorkOrderApiDto.ApiResponse.class, responseType)
//                );
//
//        Mono<WorkOrderApiDto.ApiResponse<R>> request = webClient.post()
//                .uri(endpoint)
//                .bodyValue(requestBody)
//                .retrieve()
//                .bodyToMono(typeRef)
//                .doOnSuccess(result -> logger.info("异步POST请求成功: endpoint={}, code={}",
//                        endpoint, result != null ? result.getCode() : null))
//                .doOnError(error -> logger.error("异步POST请求失败: endpoint={}, error={}",
//                        endpoint, error.getMessage()));
//
//        // 添加重试逻辑
//        if (apiProperties.isRetryEnabled()) {
//            request = request.retryWhen(Retry.backoff(apiProperties.getMaxRetries(), Duration.ofSeconds(1))
//                    .filter(this::shouldRetry)
//                    .doBeforeRetry(retrySignal ->
//                            logger.warn("重试请求: endpoint={}, attempt={}", endpoint, retrySignal.totalRetries() + 1))
//            );
//        }
//
//        return request.onErrorMap(e -> new WorkOrderApiException("异步API调用失败: " + endpoint, e));
//    }
    
    /**
     * 构建完整URL
     */
    private String buildUrl(String endpoint, Map<String, Object> queryParams) {
        StringBuilder url = new StringBuilder(endpoint);
        
        if (queryParams != null && !queryParams.isEmpty()) {
            url.append("?");
            queryParams.forEach((key, value) -> 
                    url.append(key).append("=").append(value).append("&")
            );
            url.setLength(url.length() - 1); // 移除最后一个&
        }
        
        return url.toString();
    }
    
    /**
     * 创建请求头
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("User-Agent", "Zeus-Ops-WorkOrder/1.0");
        
        // 添加认证信息
        WorkOrderApiProperties.Auth auth = apiProperties.getAuth();
        if ("BASIC".equalsIgnoreCase(auth.getType())) {
            // Basic认证在RestTemplate配置中处理
        }
        
        return headers;
    }
    
    /**
     * 判断是否应该重试
     */
    private boolean shouldRetry(Throwable throwable) {
        // 仅对网络错误和5xx错误重试
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException ex = (WebClientResponseException) throwable;
            int statusCode = ex.getStatusCode().value();
            return statusCode >= 500 || statusCode == 408 || statusCode == 429;
        }
        return throwable instanceof java.net.ConnectException 
                || throwable instanceof java.net.SocketTimeoutException;
    }
    
    /**
     * 自定义异常类
     */
    public static class WorkOrderApiException extends RuntimeException {
        public WorkOrderApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
