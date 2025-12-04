package com.gdu.zeus.ops.workorder.services;

import com.gdu.zeus.ops.workorder.client.dto.WorkOrderApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 航线服务 - 重构版
 * 支持通过配置切换真实API和Mock数据
 */
@Service
public class RouteService {

    private static final Logger logger = LoggerFactory.getLogger(RouteService.class);

    private final WorkOrderExternalService externalService;
    
    /**
     * 是否使用Mock数据
     */
    @Value("${workorder.mock-mode:false}")
    private boolean mockMode;

    public RouteService(WorkOrderExternalService externalService) {
        this.externalService = externalService;
    }

    /**
     * 根据具体位置获取可用航线
     */
    public List<WorkOrderApiDto.RouteResponseVo> getRoutesByLocation(String location, Double lon, Double lat, Double radius) {
        logger.info("查询航线: location={}, mockMode={}", location, mockMode);
        
        if (mockMode) {
            // Mock模式：返回模拟数据
            return getMockRoutes(location);
        } else {
            // 真实模式：调用外部API
            try {
                WorkOrderApiDto.RouteRequest request = WorkOrderApiDto.RouteRequest.builder()
                        .lon(lon)
                        .lat(lat)
                        .radius(radius != null ? radius : 2000.0)  // 默认2公里
                        .build();

                List<WorkOrderApiDto.RouteResponse> response =
                        externalService.getRoutes(request);
                logger.info("从API获取航线: location={}, count={}", location, response.size());
                return response.stream().map(r -> WorkOrderApiDto.RouteResponseVo.builder().routeId(r.getRouteId()).routeName(r.getRouteName()).build()).toList();
            } catch (Exception e) {
                logger.error("调用航线API失败，降级到Mock数据: location={}", location, e);
                // API调用失败时降级到Mock数据
                return getMockRoutes(location);
            }
        }
    }

    /**
     * Mock数据生成
     */
    private List<WorkOrderApiDto.RouteResponseVo> getMockRoutes(String location) {
        logger.debug("使用Mock数据: location={}", location);
        
        List<String> routes = new ArrayList<>();

        if (location.contains("光谷")) {
            routes.add("光谷广场1号巡查线");
//            routes.add("光谷广场2号巡查线");
//            routes.add("光谷广场环形巡查线");
        } else if (location.contains("普宙科技")) {
            routes.add("普宙科技周边巡查线");
            routes.add("普宙科技重点区域巡查线");
        } else {
            routes.add(location + "标准巡查线");
            routes.add(location + "快速巡查线");
        }

        logger.debug("Mock航线: {}", routes);
        return routes.stream().map(r -> WorkOrderApiDto.RouteResponseVo.builder().routeId(577).routeName(r).build()).toList();
    }
}