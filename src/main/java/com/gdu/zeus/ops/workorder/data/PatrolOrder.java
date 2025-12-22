package com.gdu.zeus.ops.workorder.data;

import com.gdu.zeus.ops.workorder.data.enums.ExecutionType;
import com.gdu.zeus.ops.workorder.data.enums.OrderNature;
import com.gdu.zeus.ops.workorder.data.enums.OrderStatus;
import com.gdu.zeus.ops.workorder.data.enums.PatrolResult;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "patrol_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatrolOrder {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String DELIMITER = ",";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 新增：工单性质（必填）
    @Enumerated(EnumType.STRING)
    @Column(name = "order_nature", nullable = false)
    private OrderNature orderNature;

    @Transient
    private String orderNatureDesc;

    @Column(name = "order_name")
    private String orderName; // 任务名称（必填，自动生成）

    @Column(name = "patrol_area")
    private String patrolArea; // 巡查区域

    @Column(name = "patrol_target")
    private String patrolTarget; // 巡查目标

    // 新增：具体位置（必填）
    @Column(name = "specific_location", nullable = false)
    private String specificLocation;

//    @ElementCollection
    @Column(name = "execution_times")
    private String executionTimes; // 执行时间列表

    @Column(name = "ai_algorithm")
    private String aiAlgorithm; // AI算法

    @Column(name = "execution_route", nullable = false)
    private String executionRoute; // 执行航线（必填）

    @Column(name = "description", length = 1000)
    private String description; // 工单描述（自动生成）

//    @ElementCollection(targetClass = PatrolResult.class)
    @Column(name = "patrol_results")
    private String patrolResults; // 巡查结果列表（必填，可多选）

    @Transient
    private String patrolResultsDesc;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_type", nullable = false)
    private ExecutionType executionType; // 执行方式（必填：单次/多个/自定义）

    @Transient
    private String executionTypeDesc;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private OrderStatus status = OrderStatus.CREATED;

    @Transient
    private String statusDesc;

    // 自定义执行规则（当 executionType 为 CUSTOM 时使用）
    @Column(name = "custom_execution_rule", length = 500)
    private String customExecutionRule;

    private Long orderId;

    private Integer orderType;

    /**
     * 构造函数 - 接收 List 参数并转换为字符串存储
     */
    public PatrolOrder(
            OrderNature orderNature,
            String orderName,
            String patrolArea,
            String patrolTarget,
            String specificLocation,
            String executionRoute,
            List<LocalDateTime> executionTimeList,
            List<PatrolResult> patrolResultList,
            ExecutionType executionType,
            String description,
            Long orderId,
            Integer orderType,
            String customExecutionRule) {

        this.orderNature = orderNature;
        this.orderName = orderName;
        this.patrolArea = patrolArea;
        this.patrolTarget = patrolTarget;
        this.specificLocation = specificLocation;
        this.executionRoute = executionRoute;
        this.executionTimes = serializeExecutionTimes(executionTimeList);
        this.patrolResults = serializePatrolResults(patrolResultList);
        this.executionType = executionType;
        this.description = description;
        this.status = OrderStatus.CREATED;
        this.orderId = orderId;
        this.orderType = orderType;
        this.customExecutionRule = customExecutionRule;
    }

    /**
     * 将执行时间列表序列化为字符串
     * 格式: "2025-10-28 14:00,2025-10-29 14:00"
     */
    private String serializeExecutionTimes(List<LocalDateTime> times) {
        if (times == null || times.isEmpty()) {
            return "";
        }
        return times.stream()
                .map(time -> time.format(FORMATTER))
                .collect(Collectors.joining(DELIMITER));
    }

    /**
     * 将巡查结果列表序列化为字符串
     * 格式: "PHOTO,VIDEO"
     */
    private String serializePatrolResults(List<PatrolResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        return results.stream()
                .map(Enum::name)
                .collect(Collectors.joining(DELIMITER));
    }

    // ========== 反序列化方法 ==========

    /**
     * 将字符串反序列化为执行时间列表
     */
    public List<LocalDateTime> getExecutionTimeList() {
        if (executionTimes == null || executionTimes.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(executionTimes.split(DELIMITER))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> LocalDateTime.parse(s, FORMATTER))
                .collect(Collectors.toList());
    }

    /**
     * 将字符串反序列化为巡查结果列表
     */
    public List<PatrolResult> getPatrolResultList() {
        if (patrolResults == null || patrolResults.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(patrolResults.split(DELIMITER))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(PatrolResult::valueOf)
                .collect(Collectors.toList());
    }
}