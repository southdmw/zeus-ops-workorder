package com.gdu.zeus.ops.workorder.data;

import com.gdu.zeus.ops.workorder.data.enums.ExecutionType;
import com.gdu.zeus.ops.workorder.data.enums.OrderStatus;
import com.gdu.zeus.ops.workorder.data.enums.OrderType;
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

    @Enumerated(EnumType.STRING)
    private OrderType orderType;

    private String orderName;
    private String patrolArea;
    private String patrolTarget;
    private LocalDateTime executionTime;
    private String executionTimes;
    private String customExecutionDesc;
    private String aiAlgorithm;
    private String executionRoute;
    private String description;
    private String selectedLocation;

    @Enumerated(EnumType.STRING)
    private PatrolResult patrolResult;
    @Transient
    private String patrolResultDesc;
    
    private String patrolResults;

    @Enumerated(EnumType.STRING)
    private ExecutionType executionType;
    @Transient
    private String executionTypeDesc;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.CREATED;
    @Transient
    private String statusDesc;
    
    public PatrolOrder(OrderType orderType, String orderName, String patrolArea, String patrolTarget,
                       LocalDateTime executionTime, String executionTimes, String customExecutionDesc,
                       String aiAlgorithm, String executionRoute, String description, 
                       String selectedLocation, String patrolResults, ExecutionType executionType) {
        this.orderType = orderType;
        this.orderName = orderName;
        this.patrolArea = patrolArea;
        this.patrolTarget = patrolTarget;
        this.executionTime = executionTime;
        this.executionTimes = executionTimes;
        this.customExecutionDesc = customExecutionDesc;
        this.aiAlgorithm = aiAlgorithm;
        this.executionRoute = executionRoute;
        this.description = description;
        this.selectedLocation = selectedLocation;
        this.patrolResults = patrolResults;
        this.executionType = executionType;
    }
}
