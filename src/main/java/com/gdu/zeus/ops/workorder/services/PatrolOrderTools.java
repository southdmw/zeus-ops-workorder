package com.gdu.zeus.ops.workorder.services;

import com.gdu.zeus.ops.workorder.client.dto.WorkOrderApiDto;
import com.gdu.zeus.ops.workorder.data.PatrolOrder;
import com.gdu.zeus.ops.workorder.data.enums.ExecutionType;
import com.gdu.zeus.ops.workorder.data.enums.OrderNature;
import com.gdu.zeus.ops.workorder.data.enums.PatrolResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PatrolOrderTools {

    private static final Logger logger = LoggerFactory.getLogger(PatrolOrderTools.class);
    private static final DateTimeFormatter CUSTOM_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private PatrolOrderService patrolOrderService;

    @Autowired
    private POIServiceV2 poiService;

    @Autowired
    private RouteServiceV2 routeService;

    @Autowired
    private PatrolOrderCreationService patrolOrderCreationService;

    /**
     * 根据巡查区域获取POI位置列表
     */
//    @Tool(description = "根据巡查区域获取具体POI位置列表，供用户选择")
//    public List<String> getPOILocations(String area) {
//        logger.info("查询POI位置，区域: {}", area);
//        return poiService.getLocationsByArea(area);
//    }

    @Tool(description = "根据巡查区域获取具体POI位置列表,供用户选择。若返回空列表,需提示用户重新确定巡查区域")
    public List<POILocationInfo> getPOILocations(@ToolParam(description = "巡查区域名称,如:光谷广场") String area) {
        logger.info("查询POI位置，区域: {}", area);
        try {
            List<WorkOrderApiDto.POILocationResponse> responses = poiService.getLocationsByArea(area);
            // 检查返回是否为空
            if (responses == null || responses.isEmpty()) {
                logger.warn("未查询到巡查区域的POI位置: {}", area);
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

            logger.info("查询到{}个POI位置", result.size());
            return result;
        } catch (Exception e) {
            logger.error("查询POI位置异常,区域: {}", area, e);
            return Collections.emptyList();
        }
    }

    /**
     * 根据具体位置获取可用航线
     */
    @Tool(description = "根据具体位置获取可用航线列表,供用户选择。若返回空列表,需提示用户重新选择具体位置或重新确认巡查区域")
    public List<WorkOrderApiDto.RouteResponseVo> getAvailableRoutes(
            @ToolParam(description = "具体位置名称")String name,
            @ToolParam(description = "具体位置经度")Double x,
            @ToolParam(description = "具体位置纬度")Double y,
            @ToolParam(description = "搜索半径，单位：米，默认2000")Double radius) {
        logger.info("查询航线,位置: {}, 经度: {}, 纬度: {}, 半径: {}米", name, x, y, radius);
        // 设置默认半径
        if (radius == null || radius <= 0) {
            radius = 2000.0;
            logger.info("使用默认搜索半径: 2000米");
        }
        try {
            List<WorkOrderApiDto.RouteResponseVo> responses =
                    routeService.getRoutesByLocation(name, x, y, radius);
            // 检查返回是否为空
            if (responses == null || responses.isEmpty()) {
                logger.warn("未查询到位置的航线: {}, 坐标: ({}, {}), 半径: {}米",
                        name, x, y, radius);
                return Collections.emptyList();
            }
            logger.info("查询到{}条航线", responses.size());
            return responses;
        } catch (Exception e) {
            logger.error("查询航线异常,位置: {}, 坐标: ({}, {})", name, x, y, e);
            return Collections.emptyList();
        }
    }

    /**
     * 创建巡查工单
     * 
     * 该方法会：
     * 1. 验证和映射所有参数
     * 2. 将工单信息保存到本地数据库
     * 3. 通过外部API调用业务系统创建工单
     * 4. Token会通过TokenContext自动传递到业务系统API
     */
    @Tool(description = "创建巡查工单，必须在收集完所有必填信息并获得用户明确同意后才能调用")
    public PatrolOrder createPatrolOrder(
            @ToolParam(description = "工单性质,如:空中巡查、野外建设巡查等")String orderNature,
            @ToolParam(description = "工单名称,自动生成,格式:区域巡查-yyyyMMddHHmmss")String orderName,
            @ToolParam(description = "巡查区域,如:光谷广场")String patrolArea,
            @ToolParam(description = "具体位置,从POI列表中用户选择的位置")String specificLocation,
            @ToolParam(description = "执行航线ID,用户选择的航线对应的ID")String routeId,
            @ToolParam(description = "执行方式:单次/多个/自定义")String executionType,
            @ToolParam(description = "执行时间,单次为单个时间,多个为逗号分隔的时间列表,格式:yyyy-MM-dd HH:mm")String executionTimes,
            @ToolParam(description = "巡查结果,可多选,格式:照片 或 视频 或 照片,视频")String patrolResults,
            @ToolParam(description = "巡查目标,如:违章停车检测")String patrolTarget,
            @ToolParam(description = "工单描述,自动生成")String description) {
        
        logger.info("创建巡查工单: {}", orderName);
        logger.info("工单性质: {}", orderNature);
        logger.info("具体位置: {}", specificLocation);
        logger.info("执行方式: {}", executionType);
        logger.info("执行时间: {}", executionTimes);
        logger.info("巡查结果: {}", patrolResults);

        try {
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
                    orderName,
                    patrolArea,
                    patrolTarget,
                    specificLocation,
                    routeId,
                    executionTimeList,
                    mappedPatrolResults,
                    mappedExecutionType,
                    description
            );

            // 1. 首先保存到本地数据库
            PatrolOrder savedOrder = patrolOrderService.createOrder(order);
            logger.info("工单已保存到本地数据库，ID: {}", savedOrder.getId());

            // 2. 调用外部业务系统API创建工单(token会通过TokenContext自动传递)
            try {
                Integer externalWorkOrderId = patrolOrderCreationService.createWorkOrderViaAPI(savedOrder);
                if (externalWorkOrderId != null && externalWorkOrderId > 0) {
                    logger.info("工单已创建到业务系统，外部工单ID: {}", externalWorkOrderId);
                } else {
                    logger.warn("工单创建到业务系统失败或返回无效的工单ID");
                }
            } catch (Exception e) {
                logger.error("调用业务系统API创建工单失败，但本地工单已保存", e);
                // 即使外部API失败，本地工单已保存，不抛出异常
            }

            return savedOrder;

        } catch (IllegalArgumentException e) {
            logger.error("参数错误: {}", e.getMessage());
            throw new RuntimeException("创建工单失败: " + e.getMessage());
        } catch (Exception e) {
            logger.error("创建工单异常", e);
            throw new RuntimeException("创建工单失败: " + e.getMessage());
        }
    }

    /**
     * 映射工单性质
     */
    private OrderNature mapOrderNature(String orderNature) {
        if (orderNature == null || orderNature.trim().isEmpty()) {
            throw new IllegalArgumentException("工单性质不能为空");
        }

        try {
            return OrderNature.fromDescription(orderNature.trim());
        } catch (IllegalArgumentException e) {
            logger.warn("未知的工单性质: {}", orderNature);
            throw e;
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
                logger.warn("未知的执行方式: {}", executionType);
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
                    logger.info("自定义执行规则: {}", executionTimes);
                    // 可以返回空列表或者当前时间作为占位
                    timeList.add(LocalDateTime.now().plusHours(1));
                    break;

                default:
                    throw new IllegalArgumentException("不支持的执行方式");
            }
        } catch (Exception e) {
            logger.error("时间解析失败: {}", executionTimes, e);
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
                    logger.warn("未知的巡查结果类型: {}", result);
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
