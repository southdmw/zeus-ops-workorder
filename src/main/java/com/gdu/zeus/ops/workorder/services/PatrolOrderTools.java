package com.gdu.zeus.ops.workorder.services;

import cn.hutool.core.convert.Convert;
import com.gdu.zeus.ops.workorder.client.dto.WorkOrderApiDto;
import com.gdu.zeus.ops.workorder.data.PatrolOrder;
import com.gdu.zeus.ops.workorder.data.enums.ExecutionType;
import com.gdu.zeus.ops.workorder.data.enums.OrderNature;
import com.gdu.zeus.ops.workorder.data.enums.PatrolResult;
import com.gdu.zeus.ops.workorder.filter.TokenContext;
import com.gdu.zeus.ops.workorder.util.ToolResultHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PatrolOrderTools {
    private static final DateTimeFormatter CUSTOM_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter ORDER_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    @Autowired
    private PatrolOrderService patrolOrderService;
    @Autowired
    private POIService poiService;
    @Autowired
    private RouteService routeService;

    @Tool(description = "根据巡查区域获取具体POI位置列表,供用户选择。若返回空列表,需提示用户重新确定巡查区域")
    public List<POILocationInfo> getPOILocations(@ToolParam(description = "巡查区域名称") String area,
                                                 ToolContext toolContext) {
        LocalDateTime startTime = LocalDateTime.now();
        log.info("========================================");
        log.info("=== Tool调用: getPOILocations ===");
        log.info("调用时间: {}", startTime.format(TIME_FORMATTER));
        log.info("输入参数: area={}", area);
        try {
            // 从ToolContext获取Token
            String token = extractTokenFromContext(toolContext);
            log.info("获取到token: {}", token);
            TokenContext.setToken(token);
            List<WorkOrderApiDto.POILocationResponse> responses = poiService.getLocationsByArea(area);
            // 检查返回是否为空
            if (responses == null || responses.isEmpty()) {
                log.info("未查询到巡查区域的POI位置: {}", area);
                logToolResult(startTime, "getPOILocations", 0, "空结果");
                return Collections.emptyList();
            }
            // 转换为包含完整信息的对象
            List<POILocationInfo> result = responses.stream()
                    .map(poi -> new POILocationInfo(
                            poi.getName(),
                            poi.getX(),  // 经度
                            poi.getY(),  // 纬度
                            poi.getAddress()
                    ))
                    .collect(Collectors.toList());
            log.info("查询到{}个POI位置", result.size());
            logToolResult(startTime, "getPOILocations", result.size(), "成功");
            return result;
        } catch (Exception e) {
            log.error("查询POI位置异常,区域: {}", area, e);
            return Collections.emptyList();
        }
    }

    /**
     * 根据具体位置获取可用航线
     */
    @Tool(description = "根据具体位置的经纬度坐标获取可用航线列表。参数说明：name=位置名称，x=经度，y=纬度，radius=搜索半径（米，默认2000）。若返回空列表，需提示用户重新选择位置或确认区域")
    public List<WorkOrderApiDto.RouteResponseVo> getAvailableRoutes(
            @ToolParam(description = "具体位置名称，例如：光谷广场-1")String name,
            @ToolParam(description = "位置经度（x坐标），例如：114.38717")Double x,
            @ToolParam(description = "位置纬度（y坐标），例如：30.50012")Double y,
            @ToolParam(description = "搜索半径，单位：米，默认2000")Double radius,
            ToolContext toolContext) {
        LocalDateTime startTime = LocalDateTime.now();
        log.info("========================================");
        log.info("=== Tool调用: getAvailableRoutes ===");
        log.info("调用时间: {}", startTime.format(TIME_FORMATTER));
        log.info("输入参数: name={}, x={}, y={}, radius={}", name, x, y, radius);
        try {
            // 从ToolContext获取Token
            String token = extractTokenFromContext(toolContext);
            log.info("Token提取: {}", token != null ? "成功" : "失败");
            TokenContext.setToken(token);
            // 设置默认半径
            double actualRadius = (radius == null || radius <= 0) ? 2000.0 : radius;
            if (radius == null || radius <= 0) {
                log.info("使用默认搜索半径: 2000米");
            }

            List<WorkOrderApiDto.RouteResponseVo> responses =
                    routeService.getRoutesByLocation(name, x, y, actualRadius);
            // 检查返回是否为空
            if (responses == null || responses.isEmpty()) {
                log.info("未查询到位置的航线: {}, 坐标: ({}, {}), 半径: {}米",
                        name, x, y, radius);
                logToolResult(startTime, "getAvailableRoutes", 0, "空结果");
                return Collections.emptyList();
            }
            log.info("查询到{}条航线", responses.size());
            logToolResult(startTime, "getAvailableRoutes", responses.size(), "成功");
            return responses;
        } catch (Exception e) {
            log.error("查询航线异常,位置: {}, 坐标: ({}, {})", name, x, y, e);
            return Collections.emptyList();
        }
    }

    /**
     * 创建巡查工单
     */
    @Tool(description = "创建巡查工单，必须在收集完所有必填信息并获得用户明确同意后才能调用")
    public PatrolOrder createPatrolOrder(
            @ToolParam(description = "工单性质,如:野外建设巡查、空中巡查、护林防火、大气探测、航空摄影、空中拍照、测绘、其他。非必填，未提供时默认为'其他'")String orderNature,        // 工单性质（必填）
            @ToolParam(description = "工单名称,自动生成,格式:区域名称+工单性质")String orderName,          // 工单名称（必填，自动生成）
            @ToolParam(description = "巡查区域,如:光谷广场")String patrolArea,         // 巡查区域
            @ToolParam(description = "具体位置,从POI列表中用户选择的位置")String specificLocation,   // 具体位置（必填）
            @ToolParam(description = "执行航线ID,用户选择的航线对应的ID")String routeId,     // 执行航线（必填）
            @ToolParam(description = "执行方式:单次/多个/自定义")String executionType,      // 执行方式（必填：单次/多个/自定义）
            @ToolParam(description = "执行时间,单次为单个时间,多个为逗号分隔的时间列表,格式:yyyy-MM-dd HH:mm")String executionTimes,     // 执行时间（必填，根据执行方式不同格式不同）
            @ToolParam(description = "巡查结果,可多选,格式:照片 或 视频 或 照片,视频")String patrolResults,      // 巡查结果（必填，可多选，逗号分隔：照片,视频）
            @ToolParam(description = "巡查目标,如:违章停车检测")String patrolTarget,       // 巡查目标
            @ToolParam(description = "工单描述,自动生成")String description,
            @ToolParam(description = "自定义执行规则(如果选择自定义执行方式)")String customExecutionRule,
            ToolContext toolContext) {      // 工单描述（自动生成）
        LocalDateTime startTime = LocalDateTime.now();
        log.info("========================================");
        log.info("=== Tool调用: createPatrolOrder ===");
        log.info("调用时间: {}", startTime.format(TIME_FORMATTER));
        log.info("输入参数:");
        log.info("  - orderNature: {}", orderNature);
        log.info("  - orderName: {}", orderName);
        log.info("  - patrolArea: {}", patrolArea);
        log.info("  - specificLocation: {}", specificLocation);
        log.info("  - routeId: {}", routeId);
        log.info("  - executionType: {}", executionType);
        log.info("  - customExecutionRule: {}", customExecutionRule);
        log.info("  - executionTimes: {}", executionTimes);
        log.info("  - patrolResults: {}", patrolResults);
        log.info("  - patrolTarget: {}", patrolTarget);
        log.info("  - description: {}", description);

        try {
            // 从ToolContext获取Token
            String token = extractTokenFromContext(toolContext);
            log.info("获取到token: {}", token);
            TokenContext.setToken(token);

            // 生成唯一的工单名称（添加时间后缀）
            String finalOrderName = generateUniqueOrderName(orderName);
            log.info("生成的完整工单名称: {}", finalOrderName);
            // 映射工单性质
            OrderNature mappedOrderNature = mapOrderNature(orderNature);

            // 映射执行方式
            ExecutionType mappedExecutionType = mapExecutionType(executionType);

            // 解析执行时间（根据执行方式不同处理不同）
            List<LocalDateTime> executionTimeList = parseExecutionTimes(executionTimes, mappedExecutionType);

            // 映射巡查结果（支持多选）
            List<PatrolResult> mappedPatrolResults = mapPatrolResults(patrolResults);

            // 构造工单对象
            PatrolOrder order = new PatrolOrder(
                    mappedOrderNature,
                    finalOrderName,
                    patrolArea,
                    patrolTarget,
                    specificLocation,
                    routeId,
                    executionTimeList,
                    mappedPatrolResults,
                    mappedExecutionType,
                    description,
                    null,
                    null,
                    customExecutionRule
            );
            PatrolOrder returnOrder = patrolOrderService.createOrder(order);
            String requestId = Convert.toStr(toolContext.getContext().get("requestId"));
            ToolResultHolder.put(requestId, "orderId" , returnOrder.getOrderId());
            ToolResultHolder.put(requestId, "orderType" , order.getOrderType());
            ToolResultHolder.put(requestId, "orderName" , order.getOrderName());

            logToolResult(startTime, "createPatrolOrder", 1, "成功");
            return returnOrder;

        } catch (IllegalArgumentException e) {
            log.error("参数错误: {}", e.getMessage());
            throw new RuntimeException("创建工单失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("创建工单异常", e);
            throw new RuntimeException("创建工单失败: " + e.getMessage());
        }
    }

    /**
     * 生成唯一的工单名称
     * 格式：基础名称-yyyyMMddHHmmssSSS
     *
     * @param baseName 基础工单名称（如"光谷广场空中巡查"）
     * @return 添加了时间后缀的唯一工单名称
     */
    private String generateUniqueOrderName(String baseName) {
        if (baseName == null || baseName.trim().isEmpty()) {
            baseName = "巡查工单";
        }

        String timestamp = LocalDateTime.now().format(ORDER_NAME_FORMATTER);
        return baseName + "-" + timestamp;
    }

    /**
     * 记录Tool调用结果
     */
    private void logToolResult(LocalDateTime startTime, String toolName, int resultCount, String status) {
        LocalDateTime endTime = LocalDateTime.now();
        long duration = java.time.Duration.between(startTime, endTime).toMillis();

        log.info("=== Tool调用完成: {} ===", toolName);
        log.info("完成时间: {}", endTime.format(TIME_FORMATTER));
        log.info("耗时: {}ms", duration);
        log.info("返回结果数量: {}", resultCount);
        log.info("状态: {}", status);
        log.info("========================================");
    }

    /**
     * 从ToolContext提取Token
     */
    private String extractTokenFromContext(ToolContext toolContext) {
        if (toolContext != null && toolContext.getContext() != null) {
            Object token = toolContext.getContext().get(TokenContext.TOKEN_KEY);
            return token != null ? token.toString() : null;
        }
        return null;
    }

    /**
     * 映射工单性质
     */
    private OrderNature mapOrderNature(String orderNature) {
        // 如果为空或null，默认返回OTHER
        if (orderNature == null || orderNature.trim().isEmpty()) {
            log.info("工单性质未提供，使用默认值: OTHER");
            return OrderNature.OTHER;
        }

        try {
            return OrderNature.fromDescription(orderNature.trim());
        } catch (IllegalArgumentException e) {
            // 如果无法识别，也返回OTHER
            log.warn("未知的工单性质: {}，使用默认值: OTHER", orderNature);
            return OrderNature.OTHER;
        }
    }

    /**
     * 映射执行方式
     */
    private ExecutionType mapExecutionType(String executionType) {
        if (executionType == null || executionType.trim().isEmpty()) {
            throw new IllegalArgumentException("执行方式不能为空");
        }

        String normalized = executionType.trim();
        switch (normalized) {
            case "单次":
            case "single":
                return ExecutionType.SINGLE;
            case "多个":
            case "multiple":
                return ExecutionType.MULTIPLE;
            case "自定义":
            case "custom":
                return ExecutionType.CUSTOM;
            default:
                log.warn("未知的执行方式: {}", executionType);
                throw new IllegalArgumentException("未知的执行方式: " + executionType);
        }
    }

    /**
     * 解析执行时间
     * 单次: "2025-10-28 14:00"
     * 多个: "2025-10-28 14:00,2025-10-29 14:00,2025-10-30 14:00"
     * 自定义: "每周一14:00" 或其他自定义规则
     */
    private List<LocalDateTime> parseExecutionTimes(String executionTimes, ExecutionType executionType) {
        if (executionTimes == null || executionTimes.trim().isEmpty()) {
            throw new IllegalArgumentException("执行时间不能为空");
        }

        List<LocalDateTime> timeList = new ArrayList<>();

        try {
            switch (executionType) {
                case SINGLE:
                    // 单次执行，解析单个时间
                    timeList.add(LocalDateTime.parse(executionTimes.trim(), CUSTOM_FORMATTER));
                    break;

                case MULTIPLE:
                    // 多个执行时间，按逗号分隔
                    String[] times = executionTimes.split(",");
                    for (String time : times) {
                        timeList.add(LocalDateTime.parse(time.trim(), CUSTOM_FORMATTER));
                    }
                    break;

                case CUSTOM:
                    // 自定义规则，这里简化处理，实际可能需要更复杂的解析逻辑
                    // 暂时存储为描述文本，不解析具体时间
                    log.info("自定义执行规则: {}", executionTimes);
                    // 可以返回空列表或者当前时间作为占位
                    timeList.add(LocalDateTime.now().plusHours(1));
                    break;

                default:
                    throw new IllegalArgumentException("不支持的执行方式");
            }
        } catch (Exception e) {
            log.error("时间解析失败: {}", executionTimes, e);
            throw new IllegalArgumentException("时间格式错误，请使用 yyyy-MM-dd HH:mm 格式");
        }

        return timeList;
    }

    /**
     * 映射巡查结果（支持多选）
     * 输入格式: "照片" 或 "视频" 或 "照片,视频"
     */
    private List<PatrolResult> mapPatrolResults(String patrolResults) {
        if (patrolResults == null || patrolResults.trim().isEmpty()) {
            throw new IllegalArgumentException("巡查结果不能为空");
        }

        List<PatrolResult> results = new ArrayList<>();
        String[] resultArray = patrolResults.split(",");

        for (String result : resultArray) {
            String normalized = result.trim();
            switch (normalized) {
                case "照片":
                case "photo":
                case "图片":
                    if (!results.contains(PatrolResult.PHOTO)) {
                        results.add(PatrolResult.PHOTO);
                    }
                    break;
                case "视频":
                case "video":
                case "录像":
                    if (!results.contains(PatrolResult.VIDEO)) {
                        results.add(PatrolResult.VIDEO);
                    }
                    break;
                default:
                    log.warn("未知的巡查结果类型: {}", result);
            }
        }

        if (results.isEmpty()) {
            throw new IllegalArgumentException("未识别到有效的巡查结果类型");
        }

        return results;
    }

    // 定义传输对象
    public record POILocationInfo(
            String name,
            Double x,
            Double y,
            String address
    ) {}
}