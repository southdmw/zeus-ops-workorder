package com.gdu.zeus.ops.workorder.services;

import com.gdu.zeus.ops.workorder.client.dto.WorkOrderApiDto;
import com.gdu.zeus.ops.workorder.data.PatrolOrder;
import com.gdu.zeus.ops.workorder.services.impl.WorkOrderExternalServiceImpl;
import com.gdu.zeus.ops.workorder.util.TokenContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 巡查工单创建服务
 * 调用外部业务系统API创建工单
 */
@Service
public class PatrolOrderCreationService {

    private static final Logger logger = LoggerFactory.getLogger(PatrolOrderCreationService.class);

    @Autowired
    private WorkOrderExternalServiceImpl workOrderExternalService;

    /**
     * 调用业务系统API创建工单
     * @param patrolOrder 巡查工单信息
     * @return 创建的工单ID
     */
    public Integer createWorkOrderViaAPI(PatrolOrder patrolOrder) {
        logger.info("通过API创建工单: {}", patrolOrder.getOrderName());
        
        try {
            // 获取当前请求的token (通过TokenContext)
            String token = TokenContext.getToken();
            if (token != null) {
                logger.debug("使用token进行工单创建: token length={}", token.length());
            } else {
                logger.warn("未获取到token，工单创建可能会失败");
            }

            // 构造创建工单请求
            WorkOrderApiDto.CreateWorkOrderRequest request = buildCreateWorkOrderRequest(patrolOrder);

            // 调用外部服务API创建工单
            Integer workOrderId = workOrderExternalService.createWorkOrder(request);
            
            if (workOrderId != null && workOrderId > 0) {
                logger.info("工单创建成功，工单ID: {}", workOrderId);
            } else {
                logger.warn("工单创建失败或返回无效的工单ID");
            }

            return workOrderId;

        } catch (Exception e) {
            logger.error("创建工单异常，工单名称: {}", patrolOrder.getOrderName(), e);
            throw new RuntimeException("创建工单失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构造创建工单的请求对象
     */
    private WorkOrderApiDto.CreateWorkOrderRequest buildCreateWorkOrderRequest(PatrolOrder patrolOrder) {
        WorkOrderApiDto.CreateWorkOrderRequest request = WorkOrderApiDto.CreateWorkOrderRequest.builder()
                .name(patrolOrder.getOrderName())
                .description(patrolOrder.getDescription())
                .source(2)  // 2 表示由AI创建
                .build();

        // 根据实际的工单信息构造更多字段
        if (patrolOrder.getPatrolArea() != null) {
            logger.debug("工单巡查区域: {}", patrolOrder.getPatrolArea());
        }

        return request;
    }
}
