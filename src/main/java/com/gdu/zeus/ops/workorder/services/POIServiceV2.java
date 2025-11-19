package com.gdu.zeus.ops.workorder.services;

import com.gdu.zeus.ops.workorder.client.dto.WorkOrderApiDto;
import com.gdu.zeus.ops.workorder.services.impl.WorkOrderExternalServiceImpl;
import com.gdu.zeus.ops.workorder.util.TokenContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * POI位置服务V2
 * 调用外部业务系统API获取POI位置信息
 */
@Service
public class POIServiceV2 {

    private static final Logger logger = LoggerFactory.getLogger(POIServiceV2.class);

    @Autowired
    private WorkOrderExternalServiceImpl workOrderExternalService;

    /**
     * 根据区域名称获取POI位置列表
     * @param area 区域名称
     * @return POI位置列表
     */
    public List<WorkOrderApiDto.POILocationResponse> getLocationsByArea(String area) {
        logger.info("查询POI位置，区域: {}", area);
        
        try {
            // 获取当前请求的token (通过TokenContext)
            String token = TokenContext.getToken();
            if (token != null) {
                logger.debug("使用token进行POI查询: token length={}", token.length());
            } else {
                logger.warn("未获取到token，POI查询可能会失败");
            }

            // 构造请求
            WorkOrderApiDto.POILocationRequest request = WorkOrderApiDto.POILocationRequest.builder()
                    .name(area)
                    .build();

            // 调用外部服务API
            List<WorkOrderApiDto.POILocationResponse> locations = workOrderExternalService.getPoiName(request);

            if (locations != null && !locations.isEmpty()) {
                logger.info("查询到{}个POI位置，区域: {}", locations.size(), area);
            } else {
                logger.warn("未查询到POI位置，区域: {}", area);
            }

            return locations;

        } catch (Exception e) {
            logger.error("查询POI位置异常，区域: {}", area, e);
            throw new RuntimeException("查询POI位置失败: " + e.getMessage(), e);
        }
    }
}
