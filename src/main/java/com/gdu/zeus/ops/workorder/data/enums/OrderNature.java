package com.gdu.zeus.ops.workorder.data.enums;

public enum OrderNature {
    FIELD_CONSTRUCTION("野外建设巡查"),
    AERIAL_PATROL("空中巡查"),
    FOREST_PROTECTION("护林防火"),
    ATMOSPHERE_DETECTION("大气探测"),
    PHOTOGRAPHY("航空摄影"),
    AERIAL_PHOTOGRAPHY("空中拍照"),
    SURVEYING("测绘"),
    OTHER("其他");

    private final String description;

    OrderNature(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static OrderNature fromDescription(String description) {
        for (OrderNature nature : OrderNature.values()) {
            if (nature.description.equals(description)) {
                return nature;
            }
        }
        throw new IllegalArgumentException("未知的工单性质: " + description);
    }
}