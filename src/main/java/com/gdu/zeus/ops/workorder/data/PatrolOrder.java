package com.gdu.zeus.ops.workorder.data;

import com.gdu.zeus.ops.workorder.data.enums.ExecutionType;
import com.gdu.zeus.ops.workorder.data.enums.OrderStatus;
import com.gdu.zeus.ops.workorder.data.enums.PatrolResult;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "patrol_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatrolOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orderName; // 任务名称
    private String patrolArea; // 巡查区域
    private String patrolTarget; // 巡查目标
    private LocalDateTime executionTime; // 执行时间
    private String aiAlgorithm; // AI算法
    private String executionRoute; // 执行航线
    private String description; // 工单描述

    @Enumerated(EnumType.STRING)
    private PatrolResult patrolResult; // 巡查结果 - 创建时指定
    @Transient
    private String patrolResultDesc;

    @Enumerated(EnumType.STRING)
    private ExecutionType executionType; // 执行方式
    @Transient
    private String executionTypeDesc;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.CREATED;
    @Transient
    private String statusDesc;
    // 构造函数
    public PatrolOrder(String orderName, String patrolArea, String patrolTarget,
                       LocalDateTime executionTime, String aiAlgorithm, String executionRoute,
                       String description, PatrolResult patrolResult, ExecutionType executionType) {
        this.orderName = orderName;
        this.patrolArea = patrolArea;
        this.patrolTarget = patrolTarget;
        this.executionTime = executionTime;
        this.aiAlgorithm = aiAlgorithm;
        this.executionRoute = executionRoute;
        this.description = description;
        this.patrolResult = patrolResult; // 创建时指定
        this.executionType = executionType;
    }
}


