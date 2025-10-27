package com.gdu.zeus.ops.workorder.services;
import com.alibaba.fastjson.JSON;
import com.gdu.zeus.ops.workorder.data.AIAlgorithm;
import com.gdu.zeus.ops.workorder.data.PatrolOrder;
import com.gdu.zeus.ops.workorder.data.enums.ExecutionType;
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

//    private static final Pattern CONSENT_PATTERN = Pattern.compile(".*(同意|确认|是的|可以|好的).*", Pattern.CASE_INSENSITIVE);

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

    /*@Tool(description = "搜索AI算法推荐")
    public List<String> searchAIAlgorithms(String keyword) {
        logger.info("搜索AI算法，关键词: {}", keyword);
        return aiAlgorithmService.searchAlgorithms(keyword);
    }*/

    @Tool(description = "查询可用航线")
    public List<String> getAvailableRoutes(String area) {
        logger.info("查询航线，区域: {}", area);
        return routeService.getRoutesByArea(area);
    }

    @Tool(description = "创建巡查工单")
    public PatrolOrder createPatrolOrder(
            String orderName,
            String patrolArea,
            String patrolTarget,
            String executionTime,
            String aiAlgorithm,
            String executionRoute,
            String description,
            String patrolResult,
            String executionType){
        logger.info("创建巡查工单: {}", orderName);
        logger.info("巡查结果类型: {}", patrolResult);
        logger.info("执行类型: {}", executionType);
//        logger.info("用户同意状态: {}", userConsent);
        // 检查用户同意
        /*if (!confirmUserConsent(userConsent)) {
            throw new IllegalArgumentException("用户未同意创建工单，操作已取消");
        }*/
        // 处理执行类型的映射
        // 处理巡查结果类型映射
        PatrolResult mappedPatrolResult = mapPatrolResult(patrolResult);
        // 处理执行类型映射
        ExecutionType mappedExecutionType = mapExecutionType(executionType);
        PatrolOrder order = null;
        try {
            order = new PatrolOrder(
                    orderName,
                    patrolArea,
                    patrolTarget,
                    parseExecutionTime(executionTime),
                    aiAlgorithm,
                    executionRoute,
                    description,
                    mappedPatrolResult,
                    mappedExecutionType
            );

        } catch (IllegalArgumentException e) {
            logger.error("非法的枚举值: {}", e.getMessage());
            throw new RuntimeException("枚举参数错误"); // 自定义异常
        }
        return patrolOrderService.createOrder(order);
    }

    @Tool(description = "按场景搜索算法")
    public List<String> searchAIAlgorithmsWithRAG(String keyword) {
        logger.info("使用RAG搜索AI算法，关键词: {}", keyword);
        // 使用向量搜索查找相关算法
        List<Document> similarDocs = vectorStore.similaritySearch(keyword);
        // 提取算法名称
        List<String> list = similarDocs.stream()
//                .filter(doc -> doc.getScore() > 0.7)
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
    /*@Tool(description = "确认用户同意创建工单")
    public boolean confirmUserConsent(String userResponse) {
        logger.info("用户响应: {}", userResponse);
        boolean agreed = CONSENT_PATTERN.matcher(userResponse.trim()).matches();
        logger.info("用户同意状态: {}", agreed);
        return agreed;
    }*/

    private PatrolResult mapPatrolResult(String patrolResult) {
        if (patrolResult == null) return PatrolResult.PHOTO; // 默认照片

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
            return LocalDateTime.now().plusHours(1); // 默认1小时后执行
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
}
