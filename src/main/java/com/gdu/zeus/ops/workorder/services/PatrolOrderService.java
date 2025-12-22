package com.gdu.zeus.ops.workorder.services;
import com.gdu.zeus.ops.workorder.client.dto.WorkOrderApiDto;
import com.gdu.zeus.ops.workorder.data.PatrolOrder;
import com.gdu.zeus.ops.workorder.data.enums.ExecutionType;
import com.gdu.zeus.ops.workorder.data.enums.OrderNature;
import com.gdu.zeus.ops.workorder.data.enums.PatrolResult;
import com.gdu.zeus.ops.workorder.repository.PatrolOrderRepository;
import com.gdu.zeus.ops.workorder.services.impl.WorkOrderExternalServiceImpl;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PatrolOrderService {

    @Autowired
    private PatrolOrderRepository repository;
    @Autowired
    private WorkOrderExternalServiceImpl workOrderExternalService;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SHORT_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    public PatrolOrder createOrder(PatrolOrder order) {
        // 1. 转换为第三方接口请求对象
        WorkOrderApiDto.CreateWorkOrderRequest request = convertToCreateRequest(order);

        // 2. 调用第三方接口创建工单
        Long externalOrderId = workOrderExternalService.createWorkOrder(request);

        if (externalOrderId != null) {
            log.info("工单已成功创建到第三方系统，外部工单ID: {}", externalOrderId);
            // 可以考虑将外部工单ID保存到PatrolOrder中（需要在实体中添加字段）
        } else {
            log.error("调用第三方接口创建工单失败");
            throw new RuntimeException("创建工单到第三方系统失败");
        }
        //3. 保存到本地数据库
        order.setOrderId(externalOrderId);
        order.setOrderType(1);
        PatrolOrder savedOrder = repository.save(order);
        log.info("工单已保存到本地数据库，ID: {}", order.getId());
        return savedOrder;
    }
    /**
     * 将PatrolOrder转换为CreateWorkOrderRequest
     */
    private WorkOrderApiDto.CreateWorkOrderRequest convertToCreateRequest(PatrolOrder order) {
        // 1. 映射工单性质ID
        String natureId = mapOrderNatureToId(order.getOrderNature());

        // 2. 映射成果类型
        List<String> achievementTypes = mapPatrolResultsToAchievementTypes(order.getPatrolResultList());

        // 3. 获取航线信息（从executionRoute字符串解析或查询）
        RouteInformation routeInfo = parseRouteInformation(order.getExecutionRoute());

        // 4. 构建执行策略
        List<WorkOrderApiDto.ExecuteStrategy> executeStrategyList =
                buildExecuteStrategy(order, routeInfo);

        // 5. 构建请求对象
        return WorkOrderApiDto.CreateWorkOrderRequest.builder()
                .name(order.getOrderName())
                .natureId(natureId)
                .description(order.getDescription())
                .supportDeptId(null)
                .attachmentList(new ArrayList<>())
                .emergencyLevel(1)  // 默认紧急程度为1
                .executeStrategyList(executeStrategyList)
                .achievementType(achievementTypes)
                .status(-1)  // 默认状态为-1 草稿
                .deviceIds(new ArrayList<>())
                .flyerIds(new ArrayList<>())
                .routeIds(new ArrayList<>())
                .relation(null)
                .type("1")  // 工单类型：1表示日常巡查
                .source(6)  // 来源：6表示AI创建
                .build();
    }

    /**
     * 构建执行策略
     */
    private List<WorkOrderApiDto.ExecuteStrategy> buildExecuteStrategy(
            PatrolOrder order, RouteInformation routeInfo) {

        List<LocalDateTime> executionTimeList = order.getExecutionTimeList();
        ExecutionType executionType = order.getExecutionType();

        WorkOrderApiDto.RouteInfo apiRouteInfo = WorkOrderApiDto.RouteInfo.builder()
                .routeName(routeInfo.getRouteName())
                .routeType(routeInfo.getRouteType())
                .existRouteType(1)
                .routeId(routeInfo.getRouteId())
                .estimatedTime(routeInfo.getEstimatedTime())
                .routeLength(routeInfo.getRouteLength())
                .pointCount(routeInfo.getPointCount())
                .build();

        // 生成格式：20251027134222
        String strategyGroupId = LocalDateTime.now()
                .format(SHORT_DATETIME_FORMATTER);

        WorkOrderApiDto.ExecuteStrategy strategy;

        switch (executionType) {
            case SINGLE:
                // 单次执行
                strategy = buildSingleExecutionStrategy(
                        executionTimeList, apiRouteInfo, strategyGroupId);
                break;

            case MULTIPLE:
                // 多次执行
                strategy = buildMultipleExecutionStrategy(
                        executionTimeList, apiRouteInfo, strategyGroupId);
                break;

            case CUSTOM:
                // 自定义执行
                strategy = buildCustomExecutionStrategy(
                        order.getCustomExecutionRule(), apiRouteInfo, strategyGroupId);
                break;

            default:
                throw new IllegalArgumentException("不支持的执行方式: " + executionType);
        }

        return Collections.singletonList(strategy);
    }

    /**
     * 构建单次执行策略
     */
    private WorkOrderApiDto.ExecuteStrategy buildSingleExecutionStrategy(
            List<LocalDateTime> executionTimeList,
            WorkOrderApiDto.RouteInfo routeInfo,
            String strategyGroupId) {

        if (executionTimeList == null || executionTimeList.isEmpty()) {
            throw new IllegalArgumentException("单次执行时间不能为空");
        }

        LocalDateTime executionTime = executionTimeList.get(0);

        WorkOrderApiDto.ExecutionTime time = WorkOrderApiDto.ExecutionTime.builder()
                .key(0)
                .value("")
                .build();

        return WorkOrderApiDto.ExecuteStrategy.builder()
                .executionTimes(Collections.singletonList(time))
                .executionStrategy(1)  // 单次执行
                .routeInfo(routeInfo)
                .sceneAreaId(null)
                .strategyGroupId(strategyGroupId)
                .existRouteType(1)
                .singleExecutionTime(executionTime.format(DATETIME_FORMATTER))
                .build();
    }

    /**
     * 构建多次执行策略
     */
    private WorkOrderApiDto.ExecuteStrategy buildMultipleExecutionStrategy(
            List<LocalDateTime> executionTimeList,
            WorkOrderApiDto.RouteInfo routeInfo,
            String strategyGroupId) {

        if (executionTimeList == null || executionTimeList.isEmpty()) {
            throw new IllegalArgumentException("多次执行时间不能为空");
        }

        // 按日期分组
        Map<LocalDate, List<LocalDateTime>> dateTimeMap = executionTimeList.stream()
                .collect(Collectors.groupingBy(LocalDateTime::toLocalDate));

        // 提取所有执行日期
        List<String> executionDates = dateTimeMap.keySet().stream()
                .sorted()
                .map(date -> date.format(DATE_FORMATTER))
                .collect(Collectors.toList());

        // 提取所有执行时间点（去重）
        Set<String> timePointSet = executionTimeList.stream()
                .map(dt -> dt.toLocalTime().format(TIME_FORMATTER))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> executionTimePoints = new ArrayList<>(timePointSet);

        // 构建执行时间列表
        List<WorkOrderApiDto.ExecutionTime> executionTimes = new ArrayList<>();
        for (int i = 0; i < executionTimePoints.size(); i++) {
            executionTimes.add(WorkOrderApiDto.ExecutionTime.builder()
                    .key(i)
                    .value(executionTimePoints.get(i))
                    .build());
        }

        return WorkOrderApiDto.ExecuteStrategy.builder()
                .executionTimes(executionTimes)
                .executionStrategy(3)  // 多次执行
                .routeInfo(routeInfo)
                .sceneAreaId(null)
                .strategyGroupId(strategyGroupId)
                .existRouteType(1)
                .moreThanOnceExecutionDate(executionDates)
                .executionTimePoint(executionTimePoints)
                .build();
    }

    /**
     * 构建自定义执行策略
     */
    private WorkOrderApiDto.ExecuteStrategy buildCustomExecutionStrategy(
            String customRule,
            WorkOrderApiDto.RouteInfo routeInfo,
            String strategyGroupId) {

        if (customRule == null || customRule.trim().isEmpty()) {
            throw new IllegalArgumentException("自定义执行规则不能为空");
        }

        return WorkOrderApiDto.ExecuteStrategy.builder()
                .executionTimes(new ArrayList<>())
                .executionStrategy(4)  // 自定义执行
                .routeInfo(routeInfo)
                .sceneAreaId(null)
                .strategyGroupId(strategyGroupId)
                .existRouteType(1)
                .executionStrategyDesc(customRule)
                .build();
    }

    /**
     * 映射工单性质到natureId
     */
    private String mapOrderNatureToId(OrderNature orderNature) {
        // 根据OrderNature枚举映射到对应的ID
        // 需要根据实际的工单性质ID进行映射
        switch (orderNature) {
            case AERIAL_PATROL:
                return "2";  // 空中巡查
            case FIELD_CONSTRUCTION:
                return "1";  // 野外建设巡查
            case FOREST_PROTECTION:
                return "3";  // 护林防御
            case ATMOSPHERE_DETECTION:
                return "4";  // 探测大气
            case PHOTOGRAPHY:
                return "5";  // 摄影
            case AERIAL_PHOTOGRAPHY:
                return "6";  // 空中摄影
            case SURVEYING:
                return "7";  // 测绘
            case OTHER:
                return "8";  // 其他 - 新增
            default:
                throw new IllegalArgumentException("未知的工单性质: " + orderNature);
        }
    }

    /**
     * 映射巡查结果到成果类型
     */
    private List<String> mapPatrolResultsToAchievementTypes(List<PatrolResult> patrolResults) {
        if (patrolResults == null || patrolResults.isEmpty()) {
            throw new IllegalArgumentException("巡查结果不能为空");
        }

        return patrolResults.stream()
                .map(result -> {
                    switch (result) {
                        case PHOTO:
                            return "1";  // 照片
                        case VIDEO:
                            return "2";  // 视频
                        default:
                            throw new IllegalArgumentException("未知的巡查结果: " + result);
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * 解析航线信息
     * 从executionRoute字符串中解析或通过routeService查询航线详细信息
     */
    private RouteInformation parseRouteInformation(String executionRoute) {
        if (executionRoute == null || executionRoute.trim().isEmpty()) {
            throw new IllegalArgumentException("执行航线不能为空");
        }

        try {
            // 尝试从routeService获取航线详细信息
            // 假设executionRoute格式为 "航线名称" 或 "航线ID:航线名称"
            // 这里需要根据实际情况调整解析逻辑

            // 方式1: 如果executionRoute就是航线名称，需要查询获取完整信息
            // 方式2: 如果有routeId，可以直接通过ID查询

            // 这里提供一个简化的实现，假设航线信息已经包含在executionRoute中
            // 实际使用时需要调用routeService.getRouteById()或类似方法获取完整信息
            WorkOrderApiDto.RouteResponse route = workOrderExternalService.getRouteInfo(WorkOrderApiDto.RouteInfoRequest.builder().routeId(executionRoute).build());
            if (route == null) {
                log.error("查询航线信息为空: {}", executionRoute);
                return null;
            }
            return RouteInformation.builder()
                    .routeId(route.getRouteId())  // 需要从实际航线信息中获取
                    .routeName(route.getRouteName())
                    .routeType(route.getType())  // 默认值，实际需要从航线信息中获取
                    .estimatedTime(route.getEstimateDuration())
                    .routeLength(route.getRouteLength())
                    .pointCount(route.getPointNum())
                    .build();

        } catch (Exception e) {
            log.error("解析航线信息失败: {}", executionRoute, e);
            throw new RuntimeException("解析航线信息失败: " + e.getMessage());
        }
    }

    public List<PatrolOrder> queryPatrolOrder(){
        List<PatrolOrder> list = repository.findAll();
        list = list.stream().map(patrolOrder->{
            PatrolOrder order = new PatrolOrder();
            BeanUtils.copyProperties(patrolOrder,order);
            order.setPatrolResultsDesc(patrolOrder.getPatrolResultsDesc());
            order.setExecutionTypeDesc(patrolOrder.getExecutionType().getDescription());
            order.setStatusDesc(patrolOrder.getStatus().getDescription());
            return order;
        }).collect(Collectors.toList());
        return list;
    }

    /**
     * 航线信息内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class RouteInformation {
        private Integer routeId;
        private String routeName;
        private Integer routeType;
        private Integer estimatedTime;
        private Double routeLength;
        private Integer pointCount;
    }
}
