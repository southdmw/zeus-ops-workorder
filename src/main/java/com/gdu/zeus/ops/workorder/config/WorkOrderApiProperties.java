package com.gdu.zeus.ops.workorder.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 工单系统API配置属性
 * 在application.yml中配置具体的API地址
 */
@Data
@Component
@ConfigurationProperties(prefix = "workorder.api")
public class WorkOrderApiProperties {

    /**
     * 工单系统基础URL
     * 示例: http://172.16.64.112:30755/gdu-domp-api
     */
    private String baseUrl;

    /**
     * 连接超时时间(毫秒)
     */
    private int connectTimeout = 5000;

    /**
     * 读取超时时间(毫秒)
     */
    private int readTimeout = 10000;

    /**
     * 是否启用重试机制
     */
    private boolean retryEnabled = true;

    /**
     * 最大重试次数
     */
    private int maxRetries = 3;

    /**
     * API认证信息
     */
    private Auth auth = new Auth();

    /**
     * 具体API端点配置
     */
    private Endpoints endpoints = new Endpoints();

    @Data
    public static class Auth {
        /**
         * 认证类型: NONE, BASIC, BEARER, API_KEY
         */
        private String type = "BEARER";

        /**
         * Bearer Token
         */
        private String bearerToken;

        /**
         * API Key (如果使用API_KEY认证)
         */
        private String apiKey;

        /**
         * API Key Header名称
         */
        private String apiKeyHeader = "X-API-Key";

        /**
         * Basic认证用户名
         */
        private String username;

        /**
         * Basic认证密码
         */
        private String password;
    }

    @Data
    public static class Endpoints {
        /**
         * 获取工单性质列表
         */
        private String natureList = "/business/workOrder/natureList";

        /**
         * 获取具体地点
         */
        private String getPoiName = "/business/overview-mode/search/getPoiName";

        /**
         * 获取已有航线
         */
        private String getRoutes = "/route/getRouteByRadius";

        /**
         * 获取航线基本信息
         */
        private String getRouteInfo = "/route/getByRouteId";

        /**
         * 创建工单
         */
        private String createWorkOrder = "/business/workOrder/create";
    }
}
