package com.gdu.zeus.ops.workorder.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 工单系统API的DTO定义
 * 根据实际API文档调整字段
 */
public class WorkOrderApiDto {

    /**
     * 工单性质响应 (对应natureList接口)
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderNatureResponse {
        private Integer id;
        private Integer dictId;
        private String itemValue;
        private String label;
        private String dictType;
        private String description;
        private Integer sortOrder;
        private String updateTime;
        private String remarks;
        private String translateKey;
    }

    /**
     * POI位置查询请求
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class POILocationRequest {
        private String name;  // POI名称
    }

    /**
     * 航线查询请求
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RouteInfoRequest {
        private String routeId;  // POI名称
    }

    /**
     * POI位置响应 (对应getPoiName接口)
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class POILocationResponse {
        private Long recordId;
        private Long mapId;
        private Double x;  // 经度
        private Double y;  // 纬度
        private String name;
        private String telNumber;
        private Long poiUid;
        private String address;
        private Integer districtId;
        private Integer searchCode;
        private String shortName;
        private String aliasName;
        private String pinYin;
        private String chainBrand;
        private String starLevel;
        private Integer shortFlag;
        private Integer exit;
        private String geom;
    }

    /**
     * 航线查询请求
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RouteRequest {
        private Integer type;      // 航线类型：1 航点航线 2 带状航线 3 面状航线
        private String factory;    // 厂商
        private Double lon;        // 经度 (必须)
        private Double lat;        // 纬度 (必须)
        private Double radius;     // 半径距离 单位 m
    }

    /**
     * 航线响应
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RouteResponse {
        private Integer routeId;
        private Integer routeSnapshotId;
        private String routeName;
        private Integer type;  // 航线类型：1 航点航线 2 带状航线 3 面状航线
        private Integer modelType;  // 航线模型 1 倾斜摄影（四面） 2 正射影像 3 航点飞行 5 航带飞行 6 倾斜摄影（五面）
        private String factory;
        private String droneTypeCode;
        private String podTypeCode;
        private Integer estimateDuration;  // 预计执行时间 单位s
        private Double routeLength;  // 航线长度 单位m
        private Integer pointNum;  // 航点数量
        private Integer shootingType;  // 拍摄类型：0 普通拍摄 1 精准复拍
        private Integer photoNum;  // 拍照数量
        private Integer videoNum;  // 视频数量 默认0
        private String coordinates;  // 经纬度坐标高度m
        private Integer status;  // 状态：0 停用 1 启用
        private String routeFilePath;
        private Integer routeFileType;  // 枚举: 1,2,3,4
        private String routeFileMD5;
        private List<RouteLabel> routeLabelList;
        private String createTime;
    }

    /**
     * 航线响应
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RouteResponseVo {
        private Integer routeId;
        private String routeName;

    }

    /**
     * 航线标签
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RouteLabel {
        private Integer id;
        private String name;
    }

    /**
     * 创建工单请求
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateWorkOrderRequest {
        private String name;  // 工单名称
        private String natureId;  // 工单性质ID
        private String description;  // 描述信息
        private String supportDeptId;  // 支持部门ID
        private List<String> attachmentList;  // 附件列表
        private Integer emergencyLevel;  // 紧急程度
        private List<ExecuteStrategy> executeStrategyList;  // 执行策略列表
        private List<String> achievementType;  // 成果类型
        private Integer status;  // 状态
        private List<String> deviceIds;  // 设备ID列表
        private List<String> flyerIds;  // 飞手ID列表
        private List<Integer> routeIds;  // 航线ID列表
        private String relation;  // 关联
        private String type;  // 工单类型
        private Integer source;  // 来源：2表示AI创建
    }

    /**
     * 执行策略
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExecuteStrategy {
        private List<ExecutionTime> executionTimes;  // 执行时间列表
        private Integer executionStrategy;  // 执行策略：1单次执行 3多次执行 4自定义
        private RouteInfo routeInfo;  // 航线信息
        private String sceneAreaId;  // 场景区域ID
        private String strategyGroupId;  // 策略组ID(根据时间戳生成)
        private Integer existRouteType;  // 已有航线类型：1
        private String singleExecutionTime;  // 单次执行时间 (executionStrategy=1时使用)
        private List<String> moreThanOnceExecutionDate;  // 多次执行日期 (executionStrategy=3时使用)
        private List<String> executionTimePoint;  // 执行时间点 (executionStrategy=3时使用)
        private String executionStrategyDesc;  // 自定义执行策略描述 (executionStrategy=4时使用)
    }

    /**
     * 执行时间
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExecutionTime {
        private Integer key;
        private String value;
    }

    /**
     * 航线信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RouteInfo {
        private String routeName;  // 航线名称
        private Integer routeType;  // 航线类型
        private Integer existRouteType;  // 已有航线类型：1
        private Integer routeId;  // 航线ID
        private Integer estimatedTime;  // 预估时间
        private Double routeLength;  // 航线长度
        private Integer pointCount;  // 航点数量
    }

    /**
     * API通用响应包装
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApiResponse<T> {
        private Integer code;  // 响应码: 0-成功, 其他-失败
        private String msg;    // 响应消息
        private T data;        // 响应数据

        public boolean isSuccess() {
            return code != null && code == 0;
        }
    }
}
