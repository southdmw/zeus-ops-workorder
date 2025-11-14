package com.gdu.zeus.ops.workorder.services.impl;

import com.gdu.zeus.ops.workorder.client.dto.WorkOrderApiDto;
import com.gdu.zeus.ops.workorder.config.WorkOrderApiProperties;
import com.gdu.zeus.ops.workorder.services.WorkOrderExternalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * 工单系统API客户端
 */
@Slf4j
@Service
public class WorkOrderExternalServiceImpl implements WorkOrderExternalService {

    private final RestTemplate restTemplate;
    private final WorkOrderApiProperties apiProperties;

    public WorkOrderExternalServiceImpl(
            @Qualifier("workOrderRestTemplate") RestTemplate restTemplate,
            WorkOrderApiProperties apiProperties) {
        this.restTemplate = restTemplate;
        this.apiProperties = apiProperties;
    }

    /**
     * 获取工单性质列表
     * GET /business/workOrder/natureList
     *
     * @return 工单性质列表
     */
    public List<WorkOrderApiDto.OrderNatureResponse> getNatureList() {
        try {
            log.info("获取工单性质列表");

            ResponseEntity<WorkOrderApiDto.ApiResponse<List<WorkOrderApiDto.OrderNatureResponse>>> response =
                    restTemplate.exchange(
                            apiProperties.getBaseUrl() + apiProperties.getEndpoints().getNatureList(),
                            HttpMethod.GET,
                            null,
                            new ParameterizedTypeReference<WorkOrderApiDto.ApiResponse<List<WorkOrderApiDto.OrderNatureResponse>>>() {}
                    );

            WorkOrderApiDto.ApiResponse<List<WorkOrderApiDto.OrderNatureResponse>> result = response.getBody();

            if (result != null && result.isSuccess()) {
                log.info("获取工单性质列表成功，共{}条", result.getData() != null ? result.getData().size() : 0);
            } else {
                log.warn("获取工单性质列表失败: {}", result != null ? result.getMsg() : "响应为空");
            }

            return result.getData();
        } catch (Exception e) {
            log.error("获取工单性质列表异常", e);
            return null;
        }
    }

    /**
     * 获取具体地点
     * POST /business/overview-mode/search/getPoiName
     *
     * @param request POI查询请求
     * @return POI位置列表
     */
    public List<WorkOrderApiDto.POILocationResponse> getPoiName(
            WorkOrderApiDto.POILocationRequest request) {
        try {
            log.info("查询POI位置，名称: {}", request.getName());

            HttpEntity<WorkOrderApiDto.POILocationRequest> httpEntity = new HttpEntity<>(request);

            ResponseEntity<WorkOrderApiDto.ApiResponse<List<WorkOrderApiDto.POILocationResponse>>> response =
                    restTemplate.exchange(
                            apiProperties.getBaseUrl() + apiProperties.getEndpoints().getGetPoiName(),
                            HttpMethod.POST,
                            httpEntity,
                            new ParameterizedTypeReference<WorkOrderApiDto.ApiResponse<List<WorkOrderApiDto.POILocationResponse>>>() {}
                    );

            WorkOrderApiDto.ApiResponse<List<WorkOrderApiDto.POILocationResponse>> result = response.getBody();

            if (result != null && result.isSuccess()) {
                log.info("查询POI位置成功，共{}条", result.getData() != null ? result.getData().size() : 0);
            } else {
                log.warn("查询POI位置失败: {}", result != null ? result.getMsg() : "响应为空");
            }

            return result.getData();
        } catch (Exception e) {
            log.error("查询POI位置异常", e);
            return null;
        }
    }

    /**
     * 获取已有航线
     * POST /business/overview-mode/search/getRoutes
     *
     * @param request 航线查询请求
     * @return 航线列表
     */
    public List<WorkOrderApiDto.RouteResponse> getRoutes(
            WorkOrderApiDto.RouteRequest request) {
        try {
            log.info("查询航线，经纬度: ({}, {}), 半径: {}m", request.getLon(), request.getLat(), request.getRadius());

            HttpEntity<WorkOrderApiDto.RouteRequest> httpEntity = new HttpEntity<>(request);

            ResponseEntity<WorkOrderApiDto.ApiResponse<List<WorkOrderApiDto.RouteResponse>>> response =
                    restTemplate.exchange(
                            apiProperties.getBaseUrl() + apiProperties.getEndpoints().getGetRoutes(),
                            HttpMethod.POST,
                            httpEntity,
                            new ParameterizedTypeReference<WorkOrderApiDto.ApiResponse<List<WorkOrderApiDto.RouteResponse>>>() {}
                    );

            WorkOrderApiDto.ApiResponse<List<WorkOrderApiDto.RouteResponse>> result = response.getBody();

            if (result != null && result.isSuccess()) {
                log.info("查询航线成功，共{}条", result.getData() != null ? result.getData().size() : 0);
            } else {
                log.warn("查询航线失败: {}", result != null ? result.getMsg() : "响应为空");
            }

            return result.getData();
        } catch (Exception e) {
            log.error("查询航线异常", e);
            return null;
        }
    }

    @Override
    public WorkOrderApiDto.RouteResponse getRouteInfo(WorkOrderApiDto.RouteInfoRequest request) {
        try {
            log.info("查询航线详情,ID: {}", request.getRouteId());

            // 使用 UriComponentsBuilder 构建 URL
            String url = UriComponentsBuilder
                    .fromHttpUrl(apiProperties.getBaseUrl() + apiProperties.getEndpoints().getGetRouteInfo())
                    .queryParam("routeId", request.getRouteId())
                    .toUriString();

            ResponseEntity<WorkOrderApiDto.ApiResponse<WorkOrderApiDto.RouteResponse>> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            null,
                            new ParameterizedTypeReference<WorkOrderApiDto.ApiResponse<WorkOrderApiDto.RouteResponse>>() {}
                    );

            WorkOrderApiDto.ApiResponse<WorkOrderApiDto.RouteResponse> result = response.getBody();

            if (result != null && result.isSuccess()) {
                log.info("查询航线基本信息成功,航线信息: {}", result.getData());
            } else {
                log.warn("查询航线基本信息失败: {}", result != null ? result.getMsg() : "响应为空");
            }

            return result.getData();
        } catch (Exception e) {
            log.error("查询航线基本信息异常", e);
            return null;
        }
    }

    /**
     * 创建工单
     * POST /business/workOrder/create
     *
     * @param request 创建工单请求
     * @return 创建的工单ID
     */
    public Integer createWorkOrder(
            WorkOrderApiDto.CreateWorkOrderRequest request) {
        try {
            log.info("创建工单，名称: {}, 性质ID: {}", request.getName(), request.getNatureId());

            HttpEntity<WorkOrderApiDto.CreateWorkOrderRequest> httpEntity = new HttpEntity<>(request);

            ResponseEntity<WorkOrderApiDto.ApiResponse<Integer>> response =
                    restTemplate.exchange(
                            apiProperties.getBaseUrl() + apiProperties.getEndpoints().getCreateWorkOrder(),
                            HttpMethod.POST,
                            httpEntity,
                            new ParameterizedTypeReference<WorkOrderApiDto.ApiResponse<Integer>>() {}
                    );

            WorkOrderApiDto.ApiResponse<Integer> result = response.getBody();

            if (result != null && result.isSuccess()) {
                log.info("创建工单成功，工单ID: {}", result.getData());
            } else {
                log.warn("创建工单失败: {}", result != null ? result.getMsg() : "响应为空");
            }

            return result.getData();
        } catch (Exception e) {
            log.error("创建工单异常", e);
            return -1;
        }
    }
}
