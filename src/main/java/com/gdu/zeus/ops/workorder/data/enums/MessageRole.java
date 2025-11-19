package com.gdu.zeus.ops.workorder.data.enums;

/**
 * 消息角色枚举
 */
public enum MessageRole {
    /**
     * 用户消息
     */
    USER("用户"),
    /**
     * 助手消息
     */
    ASSISTANT("助手");

    private final String description;

    MessageRole(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
