package com.gdu.zeus.ops.workorder.data.enums;

public enum OrderStatus {
    CREATED("已创建"),
    EXECUTING("执行中"),
    COMPLETED("已完成");
    private final String description;
    OrderStatus(String description) { this.description = description; }
    public String getDescription() {
        return description;
    }
}
