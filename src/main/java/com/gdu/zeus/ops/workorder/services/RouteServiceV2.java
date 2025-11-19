package com.gdu.zeus.ops.workorder.services;

import com.gdu.zeus.ops.workorder.client.dto.WorkOrderApiDto;
import com.gdu.zeus.ops.workorder.services.impl.WorkOrderExternalServiceImpl;
import com.gdu.zeus.ops.workorder.util.TokenContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 航线服务V2
 * 调用外部业务系统API获取航线信息
 */
@Service
public class RouteServiceV2 {

    private static final Logger logger = LoggerFactory.getLogger(RouteServiceV2.class);

    @Autowired
    private WorkOrderExternalServiceImpl workOrderExternalService;

    /**
     * 根据位置获取可用航线列表
     * @param name 位置名称
     * @param x 经度
     * @param y 纬度
     * @param radius 搜索半径 (单位: 米)
     * @return 航线响应列表 (简化的VO对象)
     */
    public List<WorkOrderApiDto.RouteResponseVo> getRoutesByLocation(String name, Double x, Double y, Double radius) {
        logger.info("查询航线，位置: {}, 经度: {}, 纬度: {}, 半径: {}米", name, x, y, radius);
        
        try {
            // 获取当前请求的token (通过TokenContext)
            String token = TokenContext.getToken();
            if (token != null) {
                logger.debug("使用token进行航线查询: token length={}", token.length());
            } else {
                logger.warn("未获取到token，航线查询可能会失败");
            }

            // 构造请求 - 使用经度(x)作为lon，纬度(y)作为lat
            WorkOrderApiDto.RouteRequest request = WorkOrderApiDto.RouteRequest.builder()
                    .lon(x)  // 经度
                    .lat(y)  // 纬度
                    .radius(radius)
                    .build();

            // 调用外部服务API
            List<WorkOrderApiDto.RouteResponse> routes = workOrderExternalService.getRoutes(request);

            if (routes != null && !routes.isEmpty()) {
                logger.info("查询到{}条航线，位置: {}", routes.size(), name);
                
                // 转换为简化的VO对象
                List<WorkOrderApiDto.RouteResponseVo> routeVos = routes.stream()
                        .map(route -> WorkOrderApiDto.RouteResponseVo.builder()
                                .routeId(route.getRouteId())
                                .routeName(route.getRouteName())
                                .build())
                        .collect(Collectors.toList());
                
                return routeVos;
            } else {
                logger.warn("未查询到航线，位置: {}", name);
                return List.of();
            }

        } catch (Exception e) {
            logger.error("查询航线异常，位置: {}", name, e);
            throw new RuntimeException("查询航线失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取航线详情
     * @param routeId 航线ID
     * @return 航线详细信息
     */
    public WorkOrderApiDto.RouteResponse getRouteDetail(String routeId) {
        logger.info("查询航线详情，航线ID: {}", routeId);
        
        try {
            // 获取当前请求的token
            String token = TokenContext.getToken();
            if (token != null) {
                logger.debug("使用token进行航线详情查询: token length={}", token.length());
            }

            WorkOrderApiDto.RouteInfoRequest request = WorkOrderApiDto.RouteInfoRequest.builder()
                    .routeId(routeId)
                    .build();

            return workOrderExternalService.getRouteInfo(request);

        } catch (Exception e) {
            logger.error("查询航线详情异常，航线ID: {}", routeId, e);
            throw new RuntimeException("查询航线详情失败: " + e.getMessage(), e);
        }
    }
}
