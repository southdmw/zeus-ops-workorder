package com.gdu.zeus.ops.workorder.data.enums;

public enum ExecutionType {

    SINGLE("单次"), MULTIPLE("多次"), PERIODIC("周期"), CUSTOM("自定义");
    private final String description;
    ExecutionType(String description) {
        this.description = description;
    }
    public String getDescription() {
        return description;
    }
}
