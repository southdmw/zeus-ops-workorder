package com.gdu.zeus.ops.workorder.services;

import com.gdu.zeus.ops.workorder.client.dto.WorkOrderApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * POI服务 - 重构版
 * 支持通过配置切换真实API和Mock数据
 */
@Service
public class POIService {

    private static final Logger logger = LoggerFactory.getLogger(POIService.class);

    private final WorkOrderExternalService externalService;

    /**
     * 是否使用Mock数据
     * 在application.yml中配置: workorder.mock-mode=true/false
     */
    @Value("${workorder.mock-mode:false}")
    private boolean mockMode;

    public POIService(WorkOrderExternalService externalService) {
        this.externalService = externalService;
    }

    /**
     * 根据区域获取POI位置列表
     */
    public List<WorkOrderApiDto.POILocationResponse> getLocationsByArea(String area) {
        logger.info("查询区域POI: area={}, mockMode={}", area, mockMode);
        if (mockMode) {
            // Mock模式：返回模拟数据
            return getMockLocations(area);
        } else {
            // 真实模式：调用外部API
            try {
                WorkOrderApiDto.POILocationRequest request = WorkOrderApiDto.POILocationRequest.builder()
                        .name(area)
                        .build();
                List<WorkOrderApiDto.POILocationResponse> response =
                        externalService.getPoiName(request);
                if (response == null || response.isEmpty()) {
                    return Collections.emptyList();
                }
                logger.info("从API获取POI位置: area={}, response={}", area, response.size(), response);
                return response;
            } catch (Exception e) {
                logger.error("调用POI API失败，降级到Mock数据: area={}", area, e);
                // API调用失败时降级到Mock数据
//                return getMockLocations(area);
                return Collections.emptyList();
            }
        }
    }

    /**
     * Mock数据生成
     */
    private List<WorkOrderApiDto.POILocationResponse> getMockLocations(String area) {
        logger.debug("使用Mock数据: area={}", area);

        List<String> locations = new ArrayList<>();

        if (area.contains("普宙科技") || area.contains("普宙")) {
            locations.add("黄龙山普宙科技");
            locations.add("未来一路普宙科技");
        } else if (area.contains("光谷广场") || area.contains("光谷")) {
            locations.add("光谷广场A出口");
            locations.add("光谷广场B出口");
            locations.add("光谷广场中心区域");
        } else {
            // 默认返回一些通用位置
            locations.add(area + "中心区域");
            locations.add(area + "周边区域");
        }

        logger.debug("Mock POI位置: {}", locations);
        List<WorkOrderApiDto.POILocationResponse> responses = new ArrayList<>();
        for (String location : locations) {
            responses.add(WorkOrderApiDto.POILocationResponse.builder()
                    .name(location)
                    .build());
        }
        return responses;
    }
}
