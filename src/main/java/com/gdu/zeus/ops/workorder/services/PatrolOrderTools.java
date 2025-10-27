package com.gdu.zeus.ops.workorder.services;
import com.alibaba.fastjson.JSON;
import com.gdu.zeus.ops.workorder.data.AIAlgorithm;
import com.gdu.zeus.ops.workorder.data.PatrolOrder;
import com.gdu.zeus.ops.workorder.data.enums.ExecutionType;
import com.gdu.zeus.ops.workorder.data.enums.OrderType;
import com.gdu.zeus.ops.workorder.data.enums.PatrolResult;
import com.gdu.zeus.ops.workorder.repository.AIAlgorithmRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PatrolOrderTools {

    private static final Logger logger = LoggerFactory.getLogger(PatrolOrderTools.class);

    private static final DateTimeFormatter CUSTOM_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private PatrolOrderService patrolOrderService;

    @Autowired
    private AIAlgorithmService aiAlgorithmService;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private AIAlgorithmRepository algorithmRepository;

    @Autowired
    private RouteService routeService;
    
    @Autowired
    private POIService poiService;

    @Tool(description = "根据巡查区域获取具体位置列表，用于让用户选择具体位置")
    public List<String> getPOILocationsByArea(String area) {
        logger.info("查询POI位置，区域: {}", area);
        return poiService.getLocationsByArea(area);
    }

    @Tool(description = "根据用户选择的具体位置获取该位置的可用航线列表")
    public List<String> getAvailableRoutesByLocation(String location) {
        logger.info("查询航线，位置: {}", location);
        return routeService.getRoutesByLocation(location);
    }

    @Tool(description = "创建日常巡查工单。注意：必须在用户明确同意后才能调用此函数，未经用户确认严禁调用")
    public PatrolOrder createDailyInspectionOrder(
            String orderType,
            String orderName,
            String patrolResults,
            String description,
            String selectedLocation,
            String executionRoute,
            String executionType,
            String executionTime,
            String executionTimes,
            String customExecutionDesc){
        logger.info("创建日常巡查工单: {}", orderName);
        logger.info("工单性质: {}", orderType);
        logger.info("巡查结果类型: {}", patrolResults);
        logger.info("执行类型: {}", executionType);
        logger.info("选择的位置: {}", selectedLocation);
        
        OrderType mappedOrderType = mapOrderType(orderType);
        ExecutionType mappedExecutionType = mapExecutionType(executionType);
        
        PatrolOrder order = null;
        try {
            LocalDateTime parsedExecutionTime = null;
            if (executionTime != null && !executionTime.isEmpty()) {
                parsedExecutionTime = parseExecutionTime(executionTime);
            }
            
            order = new PatrolOrder(
                    mappedOrderType,
                    orderName,
                    selectedLocation,
                    "",
                    parsedExecutionTime,
                    executionTimes,
                    customExecutionDesc,
                    "",
                    executionRoute,
                    description,
                    selectedLocation,
                    patrolResults,
                    mappedExecutionType
            );

        } catch (IllegalArgumentException e) {
            logger.error("非法的枚举值: {}", e.getMessage());
            throw new RuntimeException("枚举参数错误: " + e.getMessage());
        }
        return patrolOrderService.createOrder(order);
    }

    @Tool(description = "按场景搜索算法")
    public List<String> searchAIAlgorithmsWithRAG(String keyword) {
        logger.info("使用RAG搜索AI算法，关键词: {}", keyword);
        List<Document> similarDocs = vectorStore.similaritySearch(keyword);
        List<String> list = similarDocs.stream()
                .map(doc -> doc.getMetadata().get("algorithmName").toString())
                .distinct().collect(Collectors.toList());
        logger.info("返回的RAG结果: {}", JSON.toJSONString(list));
        return list;
    }

    @Tool(description = "获取算法详细信息")
    public AIAlgorithm getAlgorithmDetails(String algorithmName) {
        logger.info("获取算法详细信息，算法名称: {}", algorithmName);
        return algorithmRepository.findByAlgorithmName(algorithmName)
                .orElse(null);
    }

    private PatrolResult mapPatrolResult(String patrolResult) {
        if (patrolResult == null) return PatrolResult.PHOTO;

        String normalized = patrolResult.trim().toLowerCase();
        switch (normalized) {
            case "照片":
            case "photo":
            case "图片":
                return PatrolResult.PHOTO;
            case "视频":
            case "video":
            case "录像":
                return PatrolResult.VIDEO;
            default:
                logger.warn("未知的巡查结果类型: {}, 使用默认值 PHOTO", patrolResult);
                return PatrolResult.PHOTO;
        }
    }

    private LocalDateTime parseExecutionTime(String timeStr) {
        try {
            return LocalDateTime.parse(timeStr, CUSTOM_FORMATTER);
        } catch (Exception e) {
            logger.warn("时间解析失败: {}, 使用当前时间", timeStr);
            return LocalDateTime.now().plusHours(1);
        }
    }

    private ExecutionType mapExecutionType(String executionType) {
        if (executionType == null) return ExecutionType.SINGLE;

        String normalized = executionType.trim().toLowerCase();
        switch (normalized) {
            case "单次":
            case "single":
                return ExecutionType.SINGLE;
            case "多次":
            case "multiple":
                return ExecutionType.MULTIPLE;
            case "周期":
            case "periodic":
                return ExecutionType.PERIODIC;
            case "自定义":
            case "custom":
                return ExecutionType.CUSTOM;
            default:
                logger.warn("未知的执行类型: {}, 使用默认值 SINGLE", executionType);
                return ExecutionType.SINGLE;
        }
    }
    
    private OrderType mapOrderType(String orderType) {
        if (orderType == null) {
            throw new IllegalArgumentException("工单性质不能为空");
        }

        String normalized = orderType.trim();
        switch (normalized) {
            case "违法建设巡查":
            case "违法建设":
                return OrderType.ILLEGAL_CONSTRUCTION;
            case "空中巡查":
                return OrderType.AERIAL_PATROL;
            case "护林防火":
                return OrderType.FOREST_FIRE_PREVENTION;
            case "大气探测":
                return OrderType.ATMOSPHERIC_DETECTION;
            case "航空摄影":
                return OrderType.AERIAL_PHOTOGRAPHY;
            case "空中拍照":
                return OrderType.AERIAL_PHOTO;
            case "测绘":
                return OrderType.SURVEYING;
            case "其他":
            case "其它":
                return OrderType.OTHER;
            default:
                logger.warn("未知的工单性质: {}", orderType);
                throw new IllegalArgumentException("未知的工单性质: " + orderType);
        }
    }
}
